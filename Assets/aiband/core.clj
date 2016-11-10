;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike


(ns aiband.core
  (:require [aiband.v2d :refer :all :reload true]
            [aiband.bsp :as bsp :reload true]))

;; Load our module into the REPL
#_(require '[aiband.core :refer :all :reload true])
#_(in-ns 'aiband.core)

;; Forward definitions
(declare create-game game-state add-message-to-game)

(def level-map-string
  "Fixed string with the map of a level. Dots are floor,
   # are wall, and spaces are rock. We don't use any walls
   as we will post-process this to add in the walls."
   [
    ;01234567890124
    "                                                                                                                                "
    "      ....              .....................             ......................                      ......                    "
    "  ........              .                   .             ....                 .                     ........                   "
    "  ...........           .                   .             ....                 .                     ........                   "
    "  ...........           .   ........        .             ....                 .                 ................               "
    " ...................................        .              .                   .                 .......................        "
    " ............      .        ........        .              .                ..............       ................      .        "
    "                   .        ........        .              .                .            .         .                   .        "
    "                   .        ........        ................                .            ...........                   .        "
    "                   .        ........                .               ..............                                     .        "
    "                   .                                .              ...............                ......................        "
    "                   .                                .             ................                .                             "
    "                   .                                .            .................                .                             "
    "           .................                        .           ..................                .                             "
    "           .................                        .                                       .........                           "
    "           .................                        .                                       .........                           "
    "                   .                                .                                       .........                           "
    "                   .                                .                                       ...   ...                           "
    "                   .                                .                                       ...   ...     ....     ........     "
    "      .            .                                .                                       ...   .........  ..............     "
    "      .            .               ............     ...                                     .........                    ..     "
    "      .            .               ............     .......               .                 .........                    ..     "
    "      ..............               ............     .     .              ...                .........                    ..     "
    "                   .               ..................     .             .....                                            ..     "
    "                   .  ...          ............           .            .......                                           ..     "
    "                   .... .          ............           .           .........                                          ..     "
    "                        .          ............           .            .......                                           .....  "
    "                    .........         .                   ...................                        .......             .....  "
    "                    .       .         .                         .        ...                         .     ......            .  "
    "                    .       .............                       .         .                          .          ........     .  "
    "                    .                   .      ........         .                                    .                 .......  "
    "                    .                   .      ........         .                    .               .                          "
    "                  ......                ...............         ......               ...             .                          "
    "                 ......                        ........              .....................           .                          "
    "                ......                         ........                              .......         .                          "
    "               ......                                                                .........       .                          "
    "              ......                                      ............               ...........     .                          "
    "             ......             ...                       .                              .           .      ...........         "
    "               .                ...          .....        .                              .           .      ...........         "
    "               .             ......         ..   ..       .                              ..............................         "
    "               .             ................     ..      .     ..                       .                  ...........         "
    "               .             ......                ...    .   ......                     .                  ...........         "
    "               ....................                  ................                    .                  ...........         "
    "                             ......                           ......                     .                                      "
    "                                                                ..                       .                                      "
    "                                                                                                                                "
   ])



(defn rand-coord-seq
  "Returns an infinitely long lazy sequence of random
   [x y] coordinates within the specified bounds of width and height."
  [w h]
  (repeatedly #(do [(rand-int w) (rand-int h)])))

(defn rand-location-t
  "Returns a random coordinate in the given level with the
   specified terrain type. Gives up after 10,000 tries
   and returns nil."
  [lv terr]
  (some ; Returns the first "truthy" value (not false or nil)
    (fn [coord]
      (if (= (get2d (:terrain lv) coord) terr)
        coord
        nil))
    (take 10000 (rand-coord-seq (:width lv) (:height lv)))))





(defn char->terrain
  "Converts a character to a terrain."
  [ch]
  (case ch
    \. :floor
    \# :wall
    :rock))

(defn convert-level-from-string
  "Converts a vector/string representation of a level into our
   vector/vector of keywords representation."
  [lvstr]
  (into2d []
    (map2d-indexed
      (fn [[x y :as coords] ch]
        (let [t (char->terrain ch)]
          (if (and (= t :rock)
                   (any-neighbor= lvstr coords \.))
              :wall t)))
      lvstr)))

(def min-items
  "Minimum number of random items per level"
  15)
(def max-items
  "Maximum number of random items per level"
  50)

;;; FIXME: Make the representation of items:
;;; {[x y] [{item} ...] [x y] [{item} ...]}

(defn create-random-item-in
  "Creates a random item that is on a floor space of the
   specified level. NOT PURE."
  [lv]
  (let [[x y] (rand-location-t lv :floor)
        i-type (get [:ring :amulet] (rand-int 2))]
    {:type i-type :x x :y y}))

(defn create-items-in
  "Creates some random items in this level on floor spaces.
   NOT PURE."
  [lv]
  (into [] 
    (take (+ min-items (rand-int (- max-items min-items -1)))
      (repeatedly (fn [] (create-random-item-in lv))))))


(defn create-level-from-string
  "Creates an Aiband level from a vector of strings.
   Adds walls anywhere adjacent to floors.
   Assumes the vector of strings is perfectly square."
  [lvstr]
  (let [max-y (count lvstr)
        max-x (count (first lvstr))
        lev (convert-level-from-string lvstr)
        lv-ni {:width max-x :height max-y :terrain lev :items []} ; level with no items
        itms (create-items-in lv-ni)]
    (assoc lv-ni :items itms)))



(defn create-player
  "Creates an empty player object and places them in the level."
  ;; Place the player in the level on a random floor tile
  [level]
  (let [[start-x start-y] (rand-location-t level :floor)]
    {:x start-x :y start-y :hp 10 :hp-max 10}))

(defn player-move
  "Moves the player object in this game by the specified delta.
   Returns [new-game error] with new-game like *game* and error string.
   new-game will be nil, and error non-nil, on error."
  [game dx dy]
  (let [newgame
        (-> game
         (update-in [:player :x] (partial + dx))
         (update-in [:player :y] (partial + dy)))]
    [newgame ""]))


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
      (or (< nx 0) (< ny 0) (>= nx w) (>= ny h)) [nil "Invalid move"]
      (not= (get2d t nx ny) :floor) [nil "Invalid terrain"]
      ;; Otherwise, move the player.
      :else
        [(-> game
           (assoc-in ,,, [:player :x] nx)
           (assoc-in ,,, [:player :y] ny)) ""])))


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


(defn create-level
  "Creates a new random level"
  []
  ; (create-level-from-string level-map-string))
  ;; TODO: Save the BSP so we can create items and monsters in
  ;; rooms and corridors appropriately.
  (create-level-from-string (first (bsp/make-aiband-level 128 64))))



(defn create-messages
  "Sets up our messages list."
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
  (let [level (create-level)]
    {:level level
     :player (create-player level)
     :messages (create-messages)}))

(def game-state
  "The current full state of the Aiband game right now."
  (atom (create-game)))

