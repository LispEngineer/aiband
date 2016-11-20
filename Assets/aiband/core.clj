;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike


(ns aiband.core
  (:require [aiband.v2d :refer :all :reload true]
            [aiband.bsp :as bsp :reload true]
            [aiband.item :as i :reload true]
            [aiband.fov :as fov :reload true]
            [aiband.level :as lv :reload true]
            [aiband.player :as p :reload true]
            [aiband.random :as rnd :reload true]
            [clojure.set :as set]))

;; Load our module into the REPL
#_(require '[aiband.core :refer :all :reload true])
#_(in-ns 'aiband.core)

;; Forward definitions
(declare create-game game-state add-message-to-game)


;; Configuration --------------------------------------------------------------

(def see-dist
  "How far the player can see in a straight line, using
   Chebyshev distance."
  4)



;; Items & Level ------------------------------------------------------------
;; Functions that have a dependency on both level and item data structures
;; so we can't include them in either of those packages without creating
;; a circular dependency.

;; TODO: Have a random item generator that can then be stored
;; in the level
(defn create-random-item-in
  "Creates a random item that is on a floor space of the
   specified level. Returns as [[x y] entity]. NOT PURE."
  [lv]
  (let [[x y] (lv/rand-location-t lv :floor)
        i-type (get [:ring :amulet] (rand-int 2))]
    [[x y] (i/create-item i-type)]))

(defn create-items-in
  "Creates some random items in this level on floor spaces.
   Returns the level with the entities added.
   NOT PURE."
  [lv]
  (reduce
    (fn [lv [coord entity]]
      (assoc lv :entities (i/add-entity (:entities lv) coord entity)))
    lv
    (take (+ lv/min-items (rand-int (- lv/max-items lv/min-items -1)))
      (repeatedly (fn [] (create-random-item-in lv))))))

(defn create-level
  "Creates a new random level with items in it."
  []
  (create-items-in (lv/create-empty-level)))




;; Game Commands -----------------------------------------------------------------

;; TODO: Make this into a monadic version
(defn player-move-checked
  "Moves the player object in this game by the specified delta.
   Returns [new-game error] with new-game like *game* and error string.
   new-game will be nil, and error non-nil, on error."
  [game dx dy]
  ;; TODO: Destructure the game using destructuring-let
  (let [player (:player game)
        level  (:level game)
        p-x    (:x player)
        p-y    (:y player)
        w      (:width level)
        h      (:height level)
        t      (:terrain level)
        nx     (+ p-x dx)
        ny     (+ p-y dy)]
    (cond 
      ;; Figure out every way the move could/should fail
      (or (< nx 0) (< ny 0) (>= nx w) (>= ny h)) [nil (str "Invalid move: " nx "," ny)]
      (not= (get2d t nx ny) :floor) [nil (str "Invalid terrain: " nx "," ny)]
      ;; Otherwise, move the player.
      :else
      [(-> game
         (assoc-in ,,, [:player :x] nx)
         (assoc-in ,,, [:player :y] ny)
         ;; Update visibility
         (assoc ,,, :level (lv/update-level-visibility (:level game) [nx ny] see-dist)))
       ""])))

;; Game infrastructure --------------------------------------------------------

(defn update-game!
  "Updates the global *game* by calling the specified function
   with the current game state as the first arg with the rest of
   the args. The return value should be [new-state error]. If new-state
   is non-nil, then we save it to *game*.
   TODO: If the new-state is nil, we add the string error to the
   messages of the (previous) game state, and update *game*."
  [func & rest]
  (println func)
  (println rest)
  (let [[new-game error :as retval]
        (apply func @game-state rest)]
    (if new-game 
      (reset! game-state new-game)
      (reset! game-state (add-message-to-game @game-state error)))
    [@game-state error]))


;; ----------------------
;; ----------------------
;; ----------------------
;; XXX: FIXME: TODO: Continue refactoring here
;; (split into packages, etc.)


(defn create-messages
  "Sets up our messages list.
   :initial - The ID of the first message in the list. IDs are monotonically increasing.
   :final - The ID of the final message in the list. If the ID is the same as
            initial, then there are no messages in the list.
   :text - The messages themselves. Message with ID x is located at vector index
           (x - initial - 1). This means that the first real message will have ID 1."
  []
  {:initial 0 :final 0 :text []})

(defn add-message
  "Adds a message to a messages list set up by create-messages.
   Takes the old messages list and returns a new one."
  ;; TODO: Reduce the initial so we don't have more than the
  ;; maximum number of desired messages in our history.
  [messages new-msg]
  (let [{:keys [initial final text]} messages
        new-final (+ final 1)
        new-text (conj text new-msg)
        new-initial initial]
    {:initial new-initial :final new-final :text new-text}))

(defn add-message-to-game
  "Adds a message to a game object, returning a new game object
   that is unchanged except for the messages."
  [game new-msg]
  (assoc game :messages
    (add-message (:messages game) new-msg)))


(defn create-game
  "Creates a new game object with a player."
  []
  (let [level (create-level)
        player (p/create-player level)
        ;; Update what the player can see from the start
        level-vis (lv/update-level-visibility level [(:x player) (:y player)] see-dist)]
    {:level level-vis
     :player player
     :messages (create-messages)}))

;; The current full state of the Aiband game right now EXCEPT for
;; the random number generator seed.
;; TODO: Add a doc-string to game-state
(defonce game-state (atom nil))

(defn initialize-game
  "Initializes a game with new, random state. We don't do this in the
   'game-state' def because having it be called explicitly allows us to
   reload this file in REPL without modifying the game state."
  []
  (reset! game-state (create-game)))


;; Description -------------------------------------------------------------

(defn tile->name
  "Returns human readable tile name."
  [tile]
  (case tile
    :rock ""
    :floor "Floor"
    :wall "Wall"
    :else "Unknown"))

(defn describe-at
  "Returns a single-line string describing what the player knows about
   the location at the specified [x y] coordinates."
  [[x y :as coord]]
  (let [gs @game-state
        tile (get2d (:terrain (:level gs)) coord)
        loc-visible (contains? (:visible (:level gs)) coord)
        seen-!vis   (set/difference (:seen (:level gs)) (:visible (:level gs)))
        loc-seen    (contains? seen-!vis coord)
        player-coord [(:x (:player gs)) (:y (:player gs))]
        player-text (if (= coord player-coord) ", You" "")
        items (get (:entities (:level gs)) coord)
        item-names (map #(i/item-type->name (:item-type %)) items)
        item-str1 (apply str (interpose ", " item-names))
        item-str (if (zero? (count item-str1))
                     ""
                     (str ", " item-str1))]
    (cond
      ;; User hasn't seen this place
      (and (not loc-visible) (not loc-seen))
      "Unknown"
      ;; If for some reason he can see rock...
      (= tile :rock) 
      "Unknown"
      ;; Otherwise show him what's there (or was there, if we track that)
      :else 
      (str ;; coord " - " 
        (if loc-seen "Not visible, was: " "")
        (tile->name tile) item-str player-text))))
