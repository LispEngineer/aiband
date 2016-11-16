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
            [clojure.set :as set]))

;; Load our module into the REPL
#_(require '[aiband.core :refer :all :reload true])
#_(in-ns 'aiband.core)

;; Forward definitions
(declare create-game game-state add-message-to-game update-level-visibility)


;; Configuration --------------------------------------------------------------

;; TODO: Make these items be as a %age of level area? Room area in the level?
(def min-items
  "Minimum number of random items per level"
  50) ;15)

(def max-items
  "Maximum number of random items per level"
  250) ;50)

(def see-dist
  "How far the player can see in a straight line, using
   Chebyshev distance."
  4)


;; ----------------------------------------------------------------------------

(def level-map-string
  "Fixed string with the map of a level. Dots are floor,
   # are wall, and spaces are rock. We don't use any walls
   as we will post-process this to add in the walls."
   [
        ;00000000001111111111222222222233333333334444444444
        ;01234567890123456789012345678901234567890123456789
    #_0 "                                                                                                                                "
    #_1 "      ....              .....................             ......................                      ......                    "
    #_2 "  ........              .                   .             ....                 .                     ........                   "
    #_3 "  ...........           .                   .             ....                 .                     ........                   "
    #_4 "  ...........           .   ........        .             ....                 .                 ................               "
    #_5 " ...................................        .              .                   .                 .......................        "
    #_6 " ............      .        ........        .              .                ..............       ................      .        "
    #_7 "                   .        ........        .              .                .            .         .                   .        "
    #_8 "                   .        ........        ................                .            ...........                   .        "
    #_9 "                   .        ........                .               ..............                                     .        "
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

;;; FIXME: Make the representation of items:
;;; {[x y] [{item} ...] [x y] [{item} ...]}

(defn create-random-item-in
  "Creates a random item that is on a floor space of the
   specified level. Returns as [[x y] entity]. NOT PURE."
  [lv]
  (let [[x y] (rand-location-t lv :floor)
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
        lv-ni {:width max-x :height max-y 
               ;; What coordinates are currently visible to the player
               ;; (usually calculated from LOS, but maybe there are other
               ;; ways to see parts of the map)
               :visible #{} 
               ;; What coordinates have been seen by the player so we can
               ;; draw them (grayed out, if not visible) so the player remembers
               ;; them?
               :seen #{}
               ;; What is the 2D terrain map? (floors, walls)
               :terrain lev 
               ;; What items are in the level {coord [items]}?
               :entities (i/create-entity-location-map)}] ; level with no items
    (create-items-in lv-ni)))


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
      (or (< nx 0) (< ny 0) (>= nx w) (>= ny h)) [nil (str "Invalid move: " nx "," ny)]
      (not= (get2d t nx ny) :floor) [nil (str "Invalid terrain: " nx "," ny)]
      ;; Otherwise, move the player.
      :else
      [(-> game
         (assoc-in ,,, [:player :x] nx)
         (assoc-in ,,, [:player :y] ny)
         ;; Update visibility
         (assoc ,,, :level (update-level-visibility (:level game) [nx ny] see-dist)))
       ""])))


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
        player (create-player level)
        ;; Update what the player can see from the start
        level-vis (update-level-visibility level [(:x player) (:y player)] see-dist)]
    {:level level-vis
     :player player
     :messages (create-messages)}))

(def game-state
  "The current full state of the Aiband game right now."
  (atom nil)) ; (create-game)))

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
        player-coord [(:x (:player gs)) (:y (:player gs))]
        player-text (if (= coord player-coord) ", You" "")
        items (get (:entities (:level gs)) coord)
        item-names (map #(i/item-type->name (:item-type %)) items)
        item-str1 (apply str (interpose ", " item-names))
        item-str (if (zero? (count item-str1))
                     ""
                     (str ", " item-str1))]
    (cond
      (= tile :rock) ""
      ;; TODO: ITEMS
      :else (str coord " - " (tile->name tile) item-str player-text))))

;; Field of view -------------------------------------------------------------

(defn terrain->transparent?
  "Tells us if you can see through this terrain or not."
  [terr]
  (case terr
    :floor true
    :wall false
    :rock false ; Should never happen
    :else false)) ; Should never happen?

(defn visible-ray
  "Returns a set of all coordinates from the input seq (probably vector)
   until (and including) finding one that blocks the sight further."
  ;; External interface
  ([lv ray]
    (visible-ray (:terrain lv) ray #{}))
  ;; Internal implementation
  ([terr ray acc]
    (cond
      ;; No more to look at
      (empty? ray)
      acc
      ;; We can see through this one
      (terrain->transparent? (get2d terr (first ray)))
      (visible-ray terr (rest ray) (conj acc (first ray)))
      ;; We can't see through this one, so add it and we're done
      :else
      (conj acc (first ray)))))

(defn visible-coords
  "Calculates all the squares that can be seen from the specified
   coordinate out to the specified distance in the provided level."
  [lv coord dist]
  (let [rays (fov/los-rays coord dist)]
    (apply set/union (map #(visible-ray lv %) rays))))

(defn update-level-visibility
  "Returns a new level with the seen and visible information updated
   for a player who is standing at the specified [x y] coords and who
   can see 'dist' far away."
  [lv coord dist]
  (let [new-vis (visible-coords lv coord dist)
        new-seen (set/union (:seen lv) new-vis)]
    (if (and (= new-vis (:visible lv))
             (= new-seen (:seen lv)))
      ;; Nothing changed, so return the same object
      lv
      ;; Something changed, so return a new object with updated seen/visible
      (assoc lv :seen new-seen :visible new-vis))))

