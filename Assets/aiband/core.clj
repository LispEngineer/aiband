(ns aiband.core)

;; Forward definitions
(declare create-game game-state)

(defn create-player
  "Creates an empty player object."
  []
  {:x 0 :y 0 :hp 10 :hp-max 10})

(defn player-move
  "Moves the player object in this game by the specified delta.
   Returns [new-game error] with new-game like *game* and error string.
   new-game will be nill, and error non-nil, on error."
  [game dx dy]
  (let [newgame
        (-> game
         (update-in [:player :x] (partial + dx))
         (update-in [:player :y] (partial + dy)))]
    [newgame ""]))

(defn update-game!
  "Updates the global *game* by calling the specified function
   with the current game state as the first arg with the rest of
   the args. The return value should be [new-state error]. If new-state
   is non-nil, then we save it to *game*."
  [func & rest]
  (println func)
  (println rest)
  (let [[new-game error :as retval]
        (apply func @game-state rest)]
    (when new-game (reset! game-state new-game))
    retval))

(defn create-game
  "Creates a new game object with a player."
  []
  {:player (create-player)})

(def game-state
  "The current full state of the Aiband game right now."
  (atom (create-game)))

