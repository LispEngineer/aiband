;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike


;; One of the main things we do is try to avoid doing any GUI updates
;; unless the game state has updated. So, our main Unity loop looks like
;; this:
;;
;; Update - do any game state updates
;; LateUpdate - do any GUI state updates
;;
;; Since we can't use the Script Execution Order to sequence our hooks
;; (Edit -> Project Settings -> Script Execution Order)
;; We create one hook per call and then call out to all our other things
;; that we need to update from there. Those hooks are on an otherwise
;; unused GameObject called "Startup" in the scene.


(ns minimal.core
  (:import [UnityEngine Input KeyCode Camera Physics Time 
            UI.Text UI.ScrollRect
            Camera Resources Vector3 Quaternion Screen Canvas])
  (:require [aiband.core :as ai] ; :reload true - resets the game state when this is on
            [aiband.clrjvm :refer :all :reload true]
            [aiband.v2d :refer :all :reload true]
            [aiband.item :as i :reload true]
            [aiband.game :as game :reload true]
            [aiband.globals :as g :reload true] ; game-state and others
            [aiband.commands :as commands :reload true]
            [clojure.set :as set])
  (:use arcadia.core arcadia.linear))


;; HELPERS ------------------------------------------------------------------------------


(defn instantiate-prefab
  "name - a string. location - a Vector3.
   Loads a prefab from Assets/Resources and instantiates it at the specified location."
  [name location]
  ;; TODO: Make a memoized Resources/Load
  (let [prefab (Resources/Load name)
        thing (UnityEngine.Object/Instantiate prefab location Quaternion/identity)]
    #_(arcadia.core/log thing)
    thing))


;; GAME STATE CHANGE -------------------------------------------------------------------

;; Functions to determine if the game state has changed this frame.
;; We should call this only in the LateUpdate phases, as the game state is
;; intended to change during the Update phases.

(defn make-game-state-cache
  [frame gs changed]
  {:frame frame :gs gs :changed changed})

(defonce gsc-cache
  ;;;; "Our most recent game state changed cache."
  (atom (make-game-state-cache -1 nil false)))

(defn has-game-state-changed?
  "IMPURE: Determines if the game state has changed by checking the state of the
   previous frame number against the current frame number. Updates our game state
   changed cache for this frame if necessary. Does its work in an STM block."
  []
  (dosync
    (let [{:keys [frame gs changed]} @gsc-cache
          currentFrame (. Time frameCount)]
      #_(arcadia.core/log "Frame count:" currentFrame)
      (if (= frame currentFrame)
        ;; We already calculated this for this frame
        changed
        ;; Let's calculate for this frame and
        (if (identical? @g/game-state gs)
          ;; Nothing changed
          (do
            (reset! gsc-cache (make-game-state-cache currentFrame gs false))
            false)
          ;; Something changed
          (do
            (reset! gsc-cache (make-game-state-cache currentFrame @g/game-state true))
            true))))))


;; SCROLLABLE TEXT WINDOW -----------------------------------------------------------


(defn scroll-to-bottom
  "Scrolls the specified scrollable rectangle to the bottom. This causes an the
   Canvas to update at least twice."
  [src] ; ScrollRect Component
  (. Canvas (ForceUpdateCanvases))
  (set! (. src verticalNormalizedPosition) (float 0.0))
  (. Canvas (ForceUpdateCanvases)))

(defn get-parent
  "Gets the parent GameObject of this GameObject, or nil if we're the top
   of the hierarchy. Works around a bug in Arcadia's 'parent' routine."
  ;; In C#:
  ;; go.transform.parent.gameObject does the trick, but .parent might be null
  ;; if it has no parent, so we have to double check.
  [go]
  (let [p (.. go transform parent)]
    (if (null-obj? p)
      nil
      (. p gameObject))))

;; Test the above
#_
(do
  (get-parent (object-named "Main Camera")) ; nil
  (. (get-parent (object-named "MessageText")) name) ; AvoidContentSizeFitterError
  )


(defn parent-with-cmpt
  "Traverses the game object hierarchy upwards until it finds an object with
   the specified kind of component. Returns that game object (not the component),
   which could be the originally passed game object. Returns nil if none found."
  [go ct] ; game object, component type
  (cond
    ;; We've hit the root. Remember that Unity's null object is not Clojure's nil.
    (null-obj? go)
    nil
    ;; We don't have a component here.
    (null-obj? (cmpt go ct))
    (parent-with-cmpt (get-parent go) ct) ; No  tail recursion optimization, oh well
    ;; Found it
    :else
    go))

;; Test the above
#_
(do
  (def x (arcadia.core/object-named "MessageText"))
  (parent-with-cmpt x UnityEngine.UI.Text) ; Same as x
  (. (parent-with-cmpt x UnityEngine.UI.ScrollRect) name) ; MessageScroll
  (parent-with-cmpt x UnityEngine.Camera) ; nil
  )


(defn add-text-and-scroll
  "Appends the specified text to the specified game object's Text component,
   then walks the hierarchy of game objects upwards to find a game object with
   a ScrollRect and if found then scrolls it to the bottom."
  [go msg]
  (let [t-go (cmpt go Text)
        sr (parent-with-cmpt go ScrollRect)]
    (when (and t-go sr)
      (set! (. t-go text) (str (. t-go text) msg))
      (scroll-to-bottom (cmpt sr ScrollRect)))))



;; MOUSEOVER --------------------------------------------------------------------

(defn log-mouse-enter
  "Logs a message to unity log whenever the mouse enters something."
  [go]
  (arcadia.core/log "Mouse entered: " (. go name) (.. go transform position x) (.. go transform position y)))

(defonce last-mouse-location
  ;;;; "[x y] coordinates of the last place the mouse hovered over."
  (atom [0 0]))

(defn save-mouse-location
  "Saves the mouse location in terms of Game X,Y location to an atom every
   time it changes. Called with an OnMouseEnter hook."
  [go]
  #_(log-mouse-enter go)
  (reset! last-mouse-location
    [(int (floor (.. go transform position x))) 
     (int (floor (.. go transform position y)))]))

(defn update-mouseover-text
  "Updates the text box that describes what the mouse is hovering over.
   Floor -> Items -> Monsters -> You."
  [motgo] ; MouseOverText Game Object
  (let [msg (ai/describe-at @last-mouse-location)
        txt (cmpt motgo Text)]
    (set! (. txt text) msg)))


;; ----------------------------------------------------------------------------------


;; Log a message when arrow keys are first pushed
(defn move-when-arrow-pressed 
  "HOOK: Handles movement keys as GetKeyDown events."
  [o]
  (let [dx1 (if (Input/GetKeyDown KeyCode/LeftArrow) -1 0)
        dx2 (if (Input/GetKeyDown KeyCode/RightArrow) 1 0)
        ;; Coordinate origin 0,0 is at the bottom left, with +X -> right
        ;; and +Y -> up
        dy1 (if (Input/GetKeyDown KeyCode/UpArrow) 1 0)
        dy2 (if (Input/GetKeyDown KeyCode/DownArrow) -1 0)
        dx (+ dx1 dx2)
        dy (+ dy1 dy2)]
    (when (not (and (zero? dx) (zero? dy)))
      (arcadia.core/log "Total move delta:" dx dy)
      (game/update-game! commands/move dx dy))))

;; Updates the GUI with the latest HP, etc.
;; Call this in a LateUpdate.
(defn update-gui
  "HOOK: Updates the Unity GUI information/stats box with latest data."
  [o]
  (let [go-hp      (object-named "HPData") ; FIXME: Magic string
        go-hp-text (cmpt go-hp Text)
        go-x       (object-named "XData")  ; FIXME: Magic string
        go-x-text  (cmpt go-x Text)
        go-y       (object-named "YData")  ; FIXME: Magic string
        go-y-text  (cmpt go-y Text)
        state      @g/game-state
        player     (:player state)]
    (has-game-state-changed?)
    #_(arcadia.core/log "update-gui:" go-x-text state)
    #_(arcadia.core/log "update-gui start")
    (set! (. go-x-text text) (str (:x player)))
    (set! (. go-y-text text) (str (:y player)))
    (set! (. go-hp-text text) (str (:hp player) "/" (:hp-max player))))
    #_(arcadia.core/log "update-gui finish") )

    

;; Updates the player to show in the correct position.
;; Call this in a LateUpdate.
(defn update-player
  "HOOK: Updates the player sprite and position, and the camera."
  [go-player]
  (let [p-t (. go-player transform)
        p-p (. p-t position)
        p-state (:player @g/game-state)
        mc (object-named "Main Camera")
        mc-t (. mc transform)
        mc-p (. mc-t position)]
    #_(arcadia.core/log "Updating player to coordinates" (:x p-state) (:y p-state))
    #_(arcadia.core/log "Current position:" p-p)
    ;; Neither of the below work; you must set the entire Vec3 in the transform
    ; (set! (. p-p x) (float (:x p-state)))
    ; (set! (. p-p y) (float (:y p-state)))))
    ; (. p-p Set (float (:x p-state)) (float (:y p-state)) (. p-p z))
    (set! (. p-t position) (v3 (:x p-state) (:y p-state) 0.0))
    #_(arcadia.core/log "New position:" (. p-t position)) 
    ;; Update the camera to be in the same place as the player sprite.
    (set! (. mc-t position) (v3 (:x p-state) (:y p-state) (. mc-p z)))
    #_(arcadia.core/log "New camera position:" (. mc-t position))
    ))


;; ITEMS -------------------------------------------------------------------

;; Create a set of all items in the form [[x y] {entity-item}] from the
;; level. Compare that to the previously shown items. Remove game objects
;; for anything not still in the current set. Create game objects for anything
;; new in the current set. Game objects should be named item-<id> where ID
;; is the entity ID of that item.

(def item-tag "Unity tag for all item objects" "item")

(defn item-prefab
  "Converts an item-type keyword into a Prefab Tile name"
  [i-type]
  (case i-type
    :ring   "ItemRing"
    :amulet "ItemAmulet"
            "ItemUnknown"))

(def item-parent-name 
  "The name of the (dynamically created) game object that holds all our
   Unity GOs that represent items."
  "Items")

(defn item-tile-name
  "The name of the item Unity Game Object with the specified Entity ID."
  [entity-id]
  (str "item-" entity-id))

;; Our saved item set information
(defonce last-items
  (atom #{}))

(defn initialize-items
  "Creates the parent game object for the item game objects and resets
   the last displayed items set."
  []
  (GameObject. item-parent-name)
  (reset! last-items #{}))

(defn update-items
  "Updates all item tiles by seeing which have changed since the last
   time we updated them. Items that move location will be removed and
   re-added in the new location."
  [elm] ; entity location map
  (let [o-items  @last-items
        ;; TODO: Filter for entity :type item
        n-items  (set (i/all-items elm))
        i-remove (set/difference o-items n-items)
        i-add    (set/difference n-items o-items)
        p-go     (object-named item-parent-name)
        p-t      (. p-go transform)]
    #_(arcadia.core/log "Removing items: " i-remove)
    #_(arcadia.core/log "Adding items: " i-add)
    ;; Remove old items
    (doseq [[coord {i-id :id} :as item] i-remove]
      #_(arcadia.core/log "Removing item ID: " i-id)
      (arcadia.core/destroy (object-named (item-tile-name i-id))))
    ;; Add new items
    (doseq [[[x y] {i-id :id i-type :item-type}] i-add]
      #_(arcadia.core/log "Adding item ID: " i-id ", type: " i-type)
      (let [igo (instantiate-prefab (item-prefab i-type) (arcadia.linear/v3 x y 0.0))]
        ;; Add this to our items parent game object
        (set! (. igo name) (item-tile-name i-id))
        (. (. igo transform) SetParent p-t)))
    ;; Now that our GameObject state is updated... Update our seen cache
    (reset! last-items n-items)))

;; Visibility layer -------------------------------------------------------

;; We create a set of Game Objects which overlay the entire map.
;; They start as "Unseen" - which looks just like the background.
;; As they are revealed, they become "Visible" (which is completely
;; transparent) and then they become "Seen" after they are no longer
;; "Visible", which looks like a dimmed map tile.

(def visibility-parent-name 
  "The name of the (dynamically created) game object that holds all our
   Unity GOs that cover unseen and previously seen map tiles."
  "Visibility")

(defn visibility-tile-name
  "The name of the visibility tile at the specified location"
  [x y]
  (str "visibility-" x "," y))

;; Our saved visibility information
(defonce last-visibility
  (atom {:seen #{} :visible #{}}))

(defn vtype->prefab
  "Gets the prefab name for the visibility type."
  [vtype]
  (case vtype
    :unseen "Unseen"
    :seen "Seen"
    :visible "Visible"
    "Unknown"))

(defn initialize-visibility-layer
  "Creates all opaque background tiles over the entire map."
  [w h]
  #_(arcadia.core/log "(initialize-visibility-layer " w " " h ")")
        ;; A Unity GameObject that will hold our other Terrain GOs
  (let [pgo (GameObject. visibility-parent-name)
        ;; Transform object of the above
        pt  (. pgo transform)]
    ;; Iterate over the 2D vector including indices
    (doseq [y (range 0 h)
            x (range 0 w)]
      #_(arcadia.core/log "initialize-visibility-layer doseq " x "," y)
      (let [go (instantiate-prefab (vtype->prefab :unseen) (arcadia.linear/v3 x y 0.0))]
        ;; Set our name so we get it later
        (set! (. go name) (visibility-tile-name x y))
        ;; Put it in the Visibility parent holder
        ;; https://unity3d.com/learn/tutorials/projects/2d-roguelike-tutorial/writing-board-manager?playlist=17150
        (. (. go transform) SetParent pt)))))

(defn set-visibility-tile
  "Sets the specified coordinate's visibility tile to the specified
   visibility type :seen or :visible. Does this by removing the tile
   that was there and then instantiating a new one there."
  ;; TODO: Figure out how to swap the components instead of the whole Game Objects?
  ;; Or is Unity sufficiently efficient that it's irrelevant?
  [[x y :as coord] vtype]
  (let [pgo (object-named visibility-parent-name)
        pt  (. pgo transform)
        ;; Our old visibility game object
        ovgo (object-named (visibility-tile-name x y)) ; old vis game obj
        ;; Our new visibility game object
        nvgo (instantiate-prefab (vtype->prefab vtype) (arcadia.linear/v3 x y 0.0))]
    ;; First, get rid of our old GO
    (arcadia.core/destroy ovgo)
    ;; Now set up our new visibility game object properly
    (set! (. nvgo name) (visibility-tile-name x y))
    (. (. nvgo transform) SetParent pt)))

;; n-vis - o-vis: Make these newly visible tiles visible
;; o-vis - n-vis: Make these no longer visible tiles seen
;; n-seen - o-seen - n-vis: Make these tiles seen - this should rarely happen

(defn update-visibility-layer
  "Updates all tiles on the visibility layer by diffing the last
   updated state and the current state."
  [n-seen n-vis]
  (let [{o-seen :seen o-vis :visible} @last-visibility
        make-vis (set/difference n-vis o-vis)
        make-seen1 (set/difference o-vis n-vis)
        make-seen2 (set/difference n-seen o-seen n-vis)]
    #_(arcadia.core/log "Newly visible: " make-vis)
    #_(arcadia.core/log "Now seen 1: " make-seen1)
    #_(arcadia.core/log "Now seen 2: " make-seen2)
    ;; Update the tiles in these three sets
    (doseq [nv make-vis]
      (set-visibility-tile nv :visible))
    (doseq [ns (concat make-seen1 make-seen2)]
      (set-visibility-tile ns :seen))
    ;; Now that our GameObject state is updated... Update our seen cache
    (reset! last-visibility {:seen n-seen :visible n-vis})))



;; ------------------------------------------------------------------------
        

(defn terrain-tile
  "Converts a terrain symbol into a Prefab Tile name"
  [t]
  (cond
    (= t :wall)  "TileWall"
    (= t :floor) "TileFloor"
    :else        "TileRock"))

(def gon-main-camera "Main camera object name" "Main Camera")
(def tile-size "The (square) size of each dungeon tile" 24)

;; We use an orthographic size of half the screen height
;; divided by the size of our tiles.
;; https://blogs.unity3d.com/2015/06/19/pixel-perfect-2d/
(defn camera-setup
  "Sets up the camera so that we have a 1:1 ratio of pixels."
  []
  (arcadia.core/log "Setting up Main Camera")
  (let [camera      (object-named gon-main-camera) ; Later, get the Camera component of the Main Camera
        tile-height (int (/ Screen/height tile-size))
        tile-width  (int (/ Screen/width tile-size))
        ;; Ortho-size must be an exact fraction to make pixel perfection.
        ;; This may mean we don't use all the possible locations due to fractions of tile size.
        ortho-size  (float (/ Screen/height 2 tile-size))
        x-pos       (double (/ Screen/width 2 tile-size))]
    (arcadia.core/log "Camera:" camera)
    (arcadia.core/log "Screen:" Screen/width Screen/height)
    (arcadia.core/log "tile-height:" tile-height "tile-width:" tile-width)
    (arcadia.core/log "orthographicSize:" ortho-size "x-pos:" x-pos)
    (set! (. (cmpt camera Camera) orthographicSize) ortho-size)
    ;; TODO: Get the current transform.position and just change the x/y, leave the z
    (set! (. (. camera transform) position) (arcadia.linear/v3 x-pos ortho-size -10.0))) ; FIXME: Why Z -10?
  (arcadia.core/log "Main Camera setup complete"))

;; Set up our game. Call this once when the game is run.
;; We assume it's set up as a listener for Awake message on a Startup object
;; in the scene which doesn't otherwise do anything.
(defn game-startup
  "HOOK: Game startup/setup routine"
  [o]
  (arcadia.core/log "Game startup")
  (camera-setup)
  (game/initialize-game)
  (let [terrain (:terrain (:level @g/game-state))
        tgo (GameObject. "Terrain")  ; A Unity GameObject that will hold our other Terrain GOs
        tt  (. tgo transform)] ; The transform of the above
    #_(arcadia.core/log "Terrain" terrain)
    #_(arcadia.core/log "Screen" Screen/width Screen/height)
    ;; Iterate over the 2D vector including indices
    (doseq [[y row] (map list (range) terrain)]
      #_(arcadia.core/log "Starting terrain row" y)
      (doseq [[x t] (map list (range) row)]
        (let [go (instantiate-prefab (terrain-tile t) (arcadia.linear/v3 x y 0.0))]
          ;; Set our name
          (set! (. go name) (str "terrain-" x "," y))
          ;; We have all our floor items save the fact that the mouse is there.
          ;; This hook requires a Collider else it will not get triggered.
          ;; Hence, all our terrain prefabs need to have this information.
          (hook+ go :on-mouse-enter #'minimal.core/save-mouse-location)
          ;; Put it in the Terrain holder
          ;; https://unity3d.com/learn/tutorials/projects/2d-roguelike-tutorial/writing-board-manager?playlist=17150
          (. (. go transform) SetParent tt)
          #_(arcadia.core/log "Created" x y t go) ))
      #_(arcadia.core/log "Finished terrain row" y) )
    ;; Initialize our overlay game objects
    (initialize-visibility-layer (:width (:level @g/game-state)) (:height (:level @g/game-state)))
    (initialize-items)
    )
  (arcadia.core/log "Game startup complete"))

;; Messages ---------------------------------------------------------------------

(defonce last-message-shown
  ;;;; "What the last message number from @g/game-state :messages is that
  ;;;;  we have added to our text box."
  (atom 0))

(defn update-messages
  "Checks if there have been any new messages added to the game state, and
   if so, adds them to our Unity scrolling text box.
   TODO: Add code to remove old messages once the scrollback gets beyond a
   reasonable length (1k messages?)."
  [mtgo] ;; Message Text Game Object
  (let [lm @last-message-shown
        cm   (get-in @g/game-state [:messages :final])
        im   (get-in @g/game-state [:messages :initial])
        msgs (get-in @g/game-state [:messages :text])]
    (when (not= lm cm)
      ;; Update our messages
      (doseq [m-idx (range (- lm im -1) (+ cm 1))]
        (add-text-and-scroll mtgo (str "\n" (get msgs (- m-idx 1)))))
      (reset! last-message-shown cm))))

;; Test the above
;; Note that you'll have to reset the GameObject if Unity isn't running cause it
;; will change the Text of the default game object state.
#_
(do
  (reset! g/game-state {:messages {:initial 0 :final 0 :text []}})
  (update-messages (object-named "MessageText"))
  (reset! g/game-state {:messages {:initial 0 :final 4 :text ["Message 1" "Message 2" "Message 3" "Msg 4"]}})
  (update-messages (object-named "MessageText"))
  (update-messages (object-named "MessageText"))
  (reset! g/game-state {:messages {:initial 1 :final 4 :text ["Message 2" "Message 3" "Msg 4"]}})
  (update-messages (object-named "MessageText"))
  (reset! g/game-state {:messages {:initial 2 :final 5 :text ["Message 3" "Msg 4" "Add a 5th"]}})
  (update-messages (object-named "MessageText"))
  (update-messages (object-named "MessageText"))
  )

;; HOOKS ------------------------------------------------------------------------

(defn hook-late-update
  "Calls all our LateUpdate hooks in order with the expected game objects."
  [startup-go]
  ;; Pointer moves arounda lot, so update this every frame.
  (update-mouseover-text (object-named "MouseOverText")) ; FIXME: MAGIC STRING
  ;; Only update everything else when there's a change in state
  (when (has-game-state-changed?)
    (arcadia.core/log "Game state changed, running LateUpdate hooks, frame:" (:frame @gsc-cache))
    #_(add-text-and-scroll (object-named "MessageText") (str "\nPlaying turn " (:frame @gsc-cache)))
    (update-gui startup-go)
    (update-items (:entities (:level @g/game-state)))
    (update-visibility-layer (:seen (:level @g/game-state)) 
                             (:visible (:level @g/game-state)))
    (update-player (object-named "Player")) ; FIXME: MAGIC STRING
    (update-messages (object-named "MessageText")) ; FIXME: MAGIC STRING
    ))

(defn hook-update
  "Calls all our LateUpdate hooks in order with the expected game objects."
  [startup-go]
  (move-when-arrow-pressed startup-go))

(defn hook-start
  "Calls all our Start hooks in order with the expected game objects."
  [startup-go]
  (game-startup startup-go))

;; REPL HOOKER -------------------------------------------------------------------

(defn repl-add-all-hooks
  "Add all the hooks to all objects. Objects lose their hooks a lot."
  []
  ;; Hook the :start for standalone.
  ;; Per Ramsey Nasser, "we do funky things with awake that might get in the way of user code"
  (let [startup (object-named "Startup")]
    (doseq [h [:start :update :late-update]]
      (hook-clear startup h))
    (hook+ startup :start       #'minimal.core/hook-start)
    (hook+ startup :update      #'minimal.core/hook-update)
    (hook+ startup :late-update #'minimal.core/hook-late-update)
    ))


;; Arcadia REPL --------------------------------------------------------------------------

;; How to set up all our hooks: run the forms below in the REPL
#_
(do
  ;; Set up our REPL and hooks
  (require '[arcadia.core :refer :all])
  (require '[minimal.core :refer :all :reload true])
  (in-ns 'minimal.core)
  (repl-add-all-hooks)
  )

;; Other random things that might be useful in the REPL
#_
(do
  ;; Set up our REPL and hooks
  (require '[arcadia.core :refer :all])
  (require '[minimal.core :refer :all :reload true])
  (require '[aiband.core :refer :all :reload true])
  (in-ns 'minimal.core)
  (in-ns 'aiband.core)

  ;; Hook the move and awake callbacks
  ;; Awake works great for playing in the Unity Editor, but not in standalone Mac game:
  (hook+ (first (objects-named "Startup")) :awake #'minimal.core/game-startup)
  ;; Hook the :start for standalone.
  ;; Per Ramsey Nasser, "we do funky things with awake that might get in the way of user code"
  )
