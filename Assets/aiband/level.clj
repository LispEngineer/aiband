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
            [clojure.set :as set] ))

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

(defn create-empty-level-from-string
  "Creates an empty Aiband level from a vector of strings.
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
    lv-ni))

(defn create-empty-level
  "Creates a new random level"
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
  (fn [s]
    [true (update-level-visibility s coord dist)]))
