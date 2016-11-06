;;;; Douglas P. Fields, Jr.
;;;; symbolics at lisp.engineer - https://symbolics.lisp.engineer/
;;;; Aiband - The Artificial Intelligence Roguelike


(ns minimal.core
  (:import [UnityEngine Input KeyCode Camera Physics Time UI.Text Camera Resources Vector3 Quaternion Screen])
  (:require [aiband.core :as ai])
  (:use arcadia.core arcadia.linear #_aiband.core))



(defn create-thing
  "name - a string. location - a Vector3.
   Loads a prefab from Assets/Resources and instantiates it at the specified location."
  [name location]
  (let [prefab (Resources/Load name)
        thing (UnityEngine.Object/Instantiate prefab location Quaternion/identity)]
    #_(arcadia.core/log thing)
    thing))



;; Log a message when arrow keys are first pushed
(defn move-when-arrow-pressed [o]
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
      (ai/update-game! ai/player-move dx dy))))

;; Updates the GUI with the latest HP, etc.
;; Call this in a LateUpdate.
;; TODO: Only do anything in here if the state is changed from the last
;; time we did something in here.
(defn update-gui
  "Updates the Unity GUI items with latest data."
  [o]
  (let [go-hp      (object-named "HPData") ; FIXME: Magic string
        go-hp-text (cmpt go-hp Text)
        go-x       (object-named "XData")  ; FIXME: Magic string
        go-x-text  (cmpt go-x Text)
        go-y       (object-named "YData")  ; FIXME: Magic string
        go-y-text  (cmpt go-y Text)
        state      @ai/game-state
        player     (:player state)]
    #_(arcadia.core/log "update-gui:" go-x-text state)
    #_(arcadia.core/log "update-gui start")
    (set! (. go-x-text text) (str (:x player)))
    (set! (. go-y-text text) (str (:y player)))
    (set! (. go-hp-text text) (str (:hp player) "/" (:hp-max player))))
    #_(arcadia.core/log "update-gui finish") )

    

;; Updates the player to show in the correct position.
;; Call this in a LateUpdate.
(defn update-player
  "Updates the player sprite and position, and the camera."
  [go-player]
  (let [p-t (. go-player transform)
        p-p (. p-t position)
        p-state (:player @ai/game-state)
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


(def item-tag "Unity tag for all item objects" "item")

(defn item-prefab
  "Converts an item keyword into a Prefab Tile name"
  [i]
  (cond
    (= i :ring)  "ItemRing"
    (= i :amulet) "ItemAmulet"
    :else        "ItemUnknown"))

(defn remove-items
  "Removes all objects with the specified tag from the scene"
  []
  (let [tos (objects-tagged item-tag)]
    (doseq [o tos]
      (arcadia.core/destroy o))))

(defn add-items
  "Adds GameObjects from Prefabs for all items in the game"
  []
  ;; First create a container
  ;; Then create the items
  (let [cgo (GameObject. "Items")  ; A Unity GameObject that will hold our other Item GOs
        ct  (. cgo transform) ; The transform of the above
        items (:items (:level @ai/game-state))]
    #_(arcadia.core/log "Adding items:" items)
    (set! (. cgo tag) item-tag)
    (doseq [item items]
      (let [igo (create-thing (item-prefab (:type item)) (arcadia.linear/v3 (:x item) (:y item) 0.0))]
        ;; Do nothing
        (. (. igo transform) SetParent ct)
        ))
    #_(arcadia.core/log "Item addition complete") ))


;; Updates all items in the map.
;; Does this by deleting all objects tagged "item"
;; and then recreates them appropriately.
;; This is probably not efficient and should be revisited.
(defn update-items
  "Updates the drawing of all items in the game."
  [o]
  #_(arcadia.core/log "Removing items")
  (remove-items)
  #_(arcadia.core/log "Adding items")
  (add-items)
  #_(arcadia.core/log "Item manipulation complete") )

        

(defn terrain-tile
  "Converts a terrain symbol into a Prefab Tile name"
  [t]
  (cond
    (= t :wall)  "TileWall"
    (= t :floor) "TileFloor"
    :else        "TileRock"))

(def gon-main-camera "Main camera object name" "Main Camera")
(def tile-size "The (square) size of each dungeon tile" 32)

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
  "Game startup/setup routine"
  [o]
  (arcadia.core/log "Game startup")
  (camera-setup)
  (let [terrain (:terrain (:level @ai/game-state))
        tgo (GameObject. "Terrain")  ; A Unity GameObject that will hold our other Terrain GOs
        tt  (. tgo transform)] ; The transform of the above
    (arcadia.core/log "Terrain" terrain)
    (arcadia.core/log "Screen" Screen/width Screen/height)
    ;; Iterate over the 2D vector including indices
    (doseq [[y row] (map list (range) terrain)]
      #_(arcadia.core/log "Starting terrain row" y)
      (doseq [[x t] (map list (range) row)]
        (let [go (create-thing (terrain-tile t) (arcadia.linear/v3 x y 0.0))]
          ;; Set our name
          (set! (. go name) (str "terrain-" x "," y))
          ;; Put it in the Terrain holder
          ;; https://unity3d.com/learn/tutorials/projects/2d-roguelike-tutorial/writing-board-manager?playlist=17150
          (. (. go transform) SetParent tt)
          (arcadia.core/log "Created" x y t go)))
      #_(arcadia.core/log "Finished terrain row" y)))
  (arcadia.core/log "Game startup complete"))


;; Arcadia REPL --------------------------------------------------------------------------

#_
(do
  ;; Set up our REPL and hooks
  (require '[arcadia.core :refer :all])
  (require '[minimal.core :refer :all :reload true])
  (in-ns 'minimal.core)

  ;; Hook the move and awake callbacks
  ;; Awake works great for playing in the Unity Editor, but not in standalone Mac game
  (hook+ (first (objects-named "Startup")) :awake #'minimal.core/game-startup)
  ;; Hook the :start for standalone.
  ;; Per Ramsey Nasser, "we do funky things with awake that might get in the way of user code"
  (hook+ (first (objects-named "Startup")) :start #'minimal.core/game-startup)
  (hook+ (first (objects-named "Startup")) :update #'minimal.core/move-when-arrow-pressed)
  (hook+ (first (objects-named "Startup")) :late-update #'minimal.core/update-gui)
  (hook+ (object-named "Startup") :late-update #'minimal.core/update-items)
  (hook+ (object-named "Player") :late-update #'minimal.core/update-player)
  )
