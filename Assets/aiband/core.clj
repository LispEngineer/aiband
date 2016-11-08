;;;; Douglas P. Fields, Jr.
;;;; symbolics at lisp.engineer - https://symbolics.lisp.engineer/
;;;; Aiband - The Artificial Intelligence Roguelike


(ns aiband.core)

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

(def neighbor-deltas
  "Vector of [x y] vectors of deltas to add to current coords to get neighbors."
  [[-1 -1] [-1 0] [-1 1]
   [ 0 -1]        [ 0 1]
   [ 1 -1] [ 1 0] [ 1 1]])

(defn neighbors-of
  "Gets all the neighbors of the specified coordinate, subject to the limits provided,
   and returns them as a seq of [x y] coordinates."
  [[x y] [max-x max-y]]
  (->> neighbor-deltas
       (map (fn [[dx dy]] [(+ dx x) (+ dy y)]) ,,,) ; ,,, = where the neighbor-deltas goes
       (remove (fn [[x y]] (or (< x 0) (>= x max-x) (< y 0) (>= y max-y))) ,,,)))

(defn get2d
  "Gets the specified coordinates in a (ragged) 2d vector or other 'get'able thing.
   First deindex is row (y), second index is column (x), but the acceptable parameters
   are 'x y' or [x y]."
  ([v2d x y] (get (get v2d y) x))
  ([v2d [x y]] (get2d v2d x y)))

(defn any-neighbor=
  "Returns true if any neighbor of the specified (ragged) 2D vector location is equal to
   the specified value."
   [v2d [x y :as coords] eqval]
   (let [max-y (count v2d)
         max-x (count (first v2d))]
      #_(arcadia.core/log max-x max-y coords)
      (some #(= eqval %) 
            (map #(get2d v2d %) 
                 (neighbors-of coords [max-x max-y])))))

(defn map2d-indexed
  "Runs the specified function against each item in the sequence of sequences.
   The function's first parameter is [x y] and the second is the item at that
   location in the 2d sequence. Y is the coordinate in the outer sequence (row), and
   x is the coordinate in the inner sequence (column)."
   [func seq2d]
   (map-indexed
      (fn [y row]
        (map-indexed (fn [x itm] (func [x y] itm)) row))
      seq2d))

(defn into2d
  "Converts a seq of seq into a nested structure.
   The first parameter is used for every into, both inner and outer.
   So, it's best if it's just something like []."
  [what seq2d]
  (into what (map #(into what %) seq2d)))

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

(defn create-items-in
  "Creates some random items in this level on floor spaces."
  ;; FIXME: Make the representation of items:
  ;; {[x y] [{item} ...] [x y] [{item} ...]}
  ;; and of course put them in randomly
  [lvterr]
  [{:type :ring   :x 3 :y 3} 
   {:type :amulet :x 4 :y 4}])

(defn create-level-from-string
  "Creates an Aiband level from a vector of strings.
   Adds walls anywhere adjacent to floors.
   Assumes the vector of strings is perfectly square."
  [lvstr]
  (let [max-y (count lvstr)
        max-x (count (first lvstr))
        lev (convert-level-from-string lvstr)
        itms (create-items-in lev)]
    {:width max-x :height max-y :terrain lev :items itms}))




(defn create-player
  "Creates an empty player object."
  []
  {:x 6 :y 6 :hp 10 :hp-max 10})

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
  (create-level-from-string level-map-string))



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
  {:player (create-player)
   :level (create-level)
   :messages (create-messages)})

(def game-state
  "The current full state of the Aiband game right now."
  (atom (create-game)))

