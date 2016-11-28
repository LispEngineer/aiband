;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike

;;;; Aiband level related functionality, including test levels

(ns aiband.level
  (:require [aiband.v2d :refer :all :reload true]
            [aiband.item :as i :reload true]
            [aiband.bsp :as bsp :reload true]
            [aiband.fov :as fov :reload true]
            [aiband.random :refer :all :reload true]
            [clojure.set :as set]
            [clojure.algo.monads :as µ
             ;; We specifically don't want update-val and update-state as we
             ;; made better versions of these
             :refer [domonad with-state-field fetch-val set-val]]
            [aiband.monads :as aµ :refer :all :reload true]))

;; Configuration --------------------------------------------------------------

;; TODO: Make these items be as a %age of level area? Room area in the level?
(def min-items
  "Minimum number of random items per level"
  50) ;15)

(def max-items
  "Maximum number of random items per level"
  250) ;50)


;; Tiles ----------------------------------------------------------------------

(defn tile->name
  "Returns human readable tile name."
  [tile]
  (case tile
    :rock ""
    :floor "Floor"
    :wall "Wall"
    :else "Unknown"))



;; TEST -------------------------------------------------------------------------

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

;; Random ----------------------------------------------------------------------

(defn rand-location-t
  ;; NOTE: State game-state monad version available
  "Returns a random coordinate in the given level with the
   specified terrain type. Gives up after 10,000 tries
   and returns nil. NOT PURE."
  [lv terr]
  (some ; Returns the first "truthy" value (not false or nil)
    (fn [coord]
      (if (= (get2d (:terrain lv) coord) terr)
        coord
        nil))
    (take 10000 (rand-coord-seq (:width lv) (:height lv)))))

;; String level generation ---------------------------------------------------

(defn char->terrain
  "Converts a character to a terrain."
  [ch]
  (case ch
    \. :floor
    \# :wall
    :rock))

(defn convert-level-from-string
  "Converts a vector/string representation of a level into our
   vector/vector of keywords representation. PURE."
  [lvstr]
  (into2d []
    (map2d-indexed
      (fn [[x y :as coords] ch]
        (let [t (char->terrain ch)]
          (if (and (= t :rock)
                   (any-neighbor= lvstr coords \.))
              :wall t)))
      lvstr)))

(defn create-empty-level-from-string
  "Creates an empty Aiband level from a vector of strings.
   Adds walls anywhere adjacent to floors.
   Assumes the vector of strings is perfectly square. PURE."
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
    lv-ni))

(defn create-empty-level
  "Creates a new random level. NOT PURE."
  []
  ; (create-level-from-string level-map-string))
  ;; TODO: Save the BSP so we can create items and monsters in
  ;; rooms and corridors appropriately.
  (create-empty-level-from-string (first (bsp/make-aiband-level 128 64))))



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

(defn µ•update-visibility
  "State level: Updates the seen and visible information in the level
   for a player who is standing at the specified [x y] coords and who
   can see 'dist' far away. Returns unit which Clojure doesn't have, so
   always returns true."
  [coord dist]
  ;; This cheat implementation just calls update-level-visibility
  ;; TODO: Refactor the two into one.
  (fn [s]
    [true (update-level-visibility s coord dist)]))


;; Add doors ---------------------------------------------------------------

(def door-locations
  "Patterns where, the center of which, a door can be placed."
  [
    [ [ :any   :wall  :any   ]
      [ :floor :floor :floor ]
      [ :any   :wall  :any   ] ]

    [ [ :any   :floor :any   ]
      [ :wall  :floor :wall  ]
      [ :any   :floor :any   ] ]
  ]
)

(defn equals-or-any
  "Given two sequences of equal length, returns true if both contain the same
   contents, or the second one is :any in any unmatching location."
  ;; FIXME: Redo this as a map over the two sequences and then an every?
  [a b]
  (let [a1 (first a)
        b1 (first b)
        ar (rest a)
        br (rest b)]
    (cond
      ;; The heads don't match
      (and (not= a1 b1) (not= b1 :any))
      false
      ;; The tails are done and we never found a mismatch
      (and (empty? ar) (empty? br))
      true
      ;; Otherwise, recurse. FIXME: Use recur
      :else
      (equals-or-any ar br))))

;; Test the above
#_(do
  (map equals-or-any [[1 2 3][4 5 6][7 8 9]] [[1 2 3][4 5 6][7 8 9]])
  (map equals-or-any [[1 2 3][4 5 6][7 8 9]] [[1 2 3][4 5 6][7 8 8]]) 
  (map equals-or-any [[1 2 3][4 5 6][7 8 9]] [[1 2 3][4 5 6][7 8 :any]]))

(defn terrain-matches'
  "Given two v2ds, returns true if every entry in a is the same as b,
   or b is :any. This assumes both a and b are the exact same size."
  [a b]
  ;; Can't do it this way since and is a macro:
  ;; (apply and (map equals-or-any a b)))
  ;; See: https://clojuredocs.org/clojure.core/and
  (every? identity (map equals-or-any a b)))

;; Test the above
#_(do
  (terrain-matches' [[1 2 3][4 5 6][7 8 9]] [[1 2 3][4 5 6][7 8 9]])     
  (terrain-matches' [[1 2 3][4 5 6][7 8 9]] [[1 2 3][4 5 6][7 8 8]]) 
  (terrain-matches' [[1 2 3][4 5 6][7 8 9]] [[1 2 3][4 5 6][7 8 :any]]))

(defn terrain-matches
  "Given a level, location, and terrain subset, check if the specified
   subset matches. The subset can include an :any which will match any
   terrain type, in addition to the usual terrain types. The terrain
   subset must have odd width and height. If the [x y] would cause the
   subset to extend beyond the level bounds, this will
   return false."
  [level [x y :as loc] subset]
  (let [s-h      (count subset)
        s-w      (count (first subset))
        l-w      (:width level)
        l-h      (:height level)
        dx       (int (/ s-w 2))
        dy       (int (/ s-h 2))]
    #_(println [x y dx dy])
    (cond
      ;; Check all bounds
      (< (- x dx) 0)
      false
      (< (- y dy) 0)
      false
      (>= (+ x dx) l-w)
      false
      (>= (+ y dy) l-h)
      false
      ;; Check if we match...
      :else
      (let [terr-to-match (subvec2d (:terrain level) (- x dx) (- y dy) s-w s-h)]
        (terrain-matches' terr-to-match subset)))))

;; Test the above
#_(do
  (def lv (create-empty-level-from-string level-map-string))
  (terrain-matches lv [12 5] (first door-locations)) ; -> false
  (terrain-matches lv [13 5] (first door-locations)) ; -> true
  (terrain-matches lv [14 5] (first door-locations)) ; -> true
  (terrain-matches lv [19 5] (second door-locations)) ; -> false
  (terrain-matches lv [19 6] (second door-locations)) ; -> true
  (terrain-matches lv [19 7] (second door-locations)) ; -> true
  (some #(terrain-matches lv [19 6] %) door-locations) ; -> true
  (some #(terrain-matches lv [19 5] %) door-locations) ; -> nil (like false)
  )

; (defn ɣ•add-door
;   "State game-state: Adds a door to the specified location if it's a"

(defn ɣ•set-terrain
  "State game-state: Sets the terrain of the specified location to the
   specified type in the current level. Always returns true."
  [[x y :as coord] terr]
  (fn [s]
    (let [terrain (:terrain (:level s))
          upd-terrain (assoc2d terrain coord terr)
          upd-state (assoc-in s [:level :terrain] upd-terrain)]
      [true upd-state])))


(defn ɣ•add-doors
  "State game-state: Adds up to n random closed doors to the current level.
   Returns number of random doors actually added."
  [n]
  (letfn [(good-door-loc? [lv coord]
            (some (fn [pattern] (terrain-matches lv coord pattern)) door-locations))]
    (dostate
      [coord (ɣ•rand-location-p good-door-loc?)
       ; _ (<- (println coord))
       :cond [
         ;; Couldn't find a place to put a door
         (nil? coord)
         [retval (<- 0)]
         ;; Last door?
         (<= n 1)
         [_      (ɣ•set-terrain coord :door-closed) 
          retval (<- 1)]
         ;; More doors still to add
         :else
         [_       (ɣ•set-terrain coord :door-closed)
          retval' (ɣ•add-doors (dec n))
          retval  (<- (inc retval'))]]]
       retval)))

;; Test the above
#_(do
  (def gs (assoc investigate.monads/test-game-state :level (create-empty-level-from-string level-map-string)))
  (first ((ɣ•add-doors 200) gs))) ; -> 187

