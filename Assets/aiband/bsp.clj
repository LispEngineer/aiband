;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer

(ns aiband.bsp
  (:require [aiband.v2d    :refer :all :reload true]
            [aiband.clrjvm :refer :all :reload true]))

;; Binary Space Partitioning algorithm
;;
;; BSP data structure is inclusive, so the min and max numbers are all
;; valid coordinates. So, a width 10 and height 10 BSP entry will have
;; min 0, max 9 of each for x and y.
;;
;; Anyway, for more information on BSP, check out Chapter Three of
;; _Procedural Content Generation in Games_ at http://pcgbook.com/ .


#_(require ['aiband.bsp :refer :all :reload true])
#_(in-ns 'aiband.bsp)
#_(use 'clojure.pprint)



(def min-dim
  "Minimum dimension after fully partitioned"
  6)

(def min-area
  "Minimum area for a partitioning"
  (* (inc (* 2 min-dim)) 
     (inc (* 2 min-dim))))

(def min-area-mult
  "Minimum multiple of the area for 100% chance of splitting"
  3)

(def min-split-chance
  "Base chance of splitting something with the minimal area (decimal 0-1)."
  0.10)

(def max-split-chance
  "Base chance of splitting something with the maximum area before guaranteed
   split."
  0.85)

(defn make-bsp
  [min-x min-y max-x max-y children]
  { :min-x min-x :min-y min-y :max-x max-x :max-y max-y
    :children (into [] children) })

(defn split
  "Splits a bsp randomly either horizontally or vertically.
   Returns list of two new bsps. Always leaves a column/row
   of buffer between the two. As such, make sure that the
   width or height of this input bsp is at least 3."
  ;; TODO: Add a variable size buffer between them for more variety, if space allows
  [bi] ; bsp-in
  ;; Only split an axis if it's big enough
  (let [vs  ; vertical split
          (cond
            ;; Is X too small to split vertically?
            (<= (- (:max-x bi) (:min-x bi)) (* 2 min-dim))
            false
            ;; Is Y too small to split horizontally?
            (<= (- (:max-y bi) (:min-y bi)) (* 2 min-dim))
            true
            ;; Randomly pick something to split
            :else
            (= 1 (rand-int 2)))
        min-d (if vs (:min-x bi) (:min-y bi))
        max-d (if vs (:max-x bi) (:max-y bi))
        d-split (+ min-d min-dim (rand-int (- max-d min-d min-dim min-dim)))]
    ;; First our lesser half, then our greater half
    [(make-bsp (:min-x bi) (:min-y bi)
               (if vs (- d-split 1) (:max-x bi))
               (if vs (:max-y bi) (- d-split 1)) [])
     (make-bsp (if vs (+ d-split 1) (:min-x bi))
               (if vs (:min-y bi) (+ d-split 1))
               (:max-x bi) (:max-y bi) [])]))

(defn split?
  "Decides if we should split this BSP.
   It has to be big enough to split, but also
   if not super-big has only a chance to split.
   This is _not_ a pure function."
  [bi] ; BSP in
  (let [w (- (:max-x bi) (:min-x bi) -1)
        h (- (:max-y bi) (:min-y bi) -1)]
    (if 
      ;; Big enough to split?
      (and (or (> w (* 2 min-dim))
               (> h (* 2 min-dim)))
            (> (* w h) min-area))
      (if (> (* w h) (* min-area min-area-mult))
        ;; Guaranteed split - too big
        true
        ;; Randomly guess if we split
        (let [mult-of-min (/ (* w h) min-area)
              ;; mult-of-min will be 1 to min-area-mult
              ;; scale it to 0-1
              portion-to-split (/ (dec mult-of-min) (dec min-area-mult))
              chance-to-split (+ min-split-chance
                                 (* portion-to-split (- max-split-chance min-split-chance)))]
          #_(println "mult-of-min:" mult-of-min "portion-to-split:" portion-to-split
                   "chance-to-split:" chance-to-split)
          ;; Split if our random number says to do it...
          (<= (rand) chance-to-split)))
      ;; Not big enough to split
      false)))


(defn partition-bsp
  "Returns the input bsp with children added, or
   if it's too small to partition, unchagned."
  [bsp-in]
  ;; TODO: Only partition it with some percentage chance proportional to how much
  ;;       bigger than the minimum size this partition is.
  ;; The -1 is because if it's exactly one column/row, it's still that wide
  (let [w (- (:max-x bsp-in) (:min-x bsp-in) -1)
        h (- (:max-y bsp-in) (:min-y bsp-in) -1)]
    ;; FIXME: Split it if EITHER dimension is big enough
    (if (split? bsp-in)
      ;; Partition this one and its (new) children
      (assoc bsp-in :children (mapv partition-bsp (split bsp-in)))
      ;; Don't partition
      bsp-in)))

;; Returns a structure like this:
;; { :min-x :min-y :max-x :max-y :children [...] }
(defn gen-bsp
  [w h]
  { :min-x 0 :max-x (dec w) :min-y 0 :max-y (dec h) :children () })




(defn visualize-internal
  "Recursively fills in the 2d vector of characters with letters representing each
   node of the BSP."
  [bsp v node-num]
  (if (nil? bsp)
    ;; Terminal case - no bsp
    [v node-num]
    ;; Recursive case - a bsp
    (let [node-char (char (+ (int \A) node-num))
          v-node (map2d-indexed 
                  (constantly node-char)
                  [(:min-x bsp) (:min-y bsp) (:max-x bsp) (:max-y bsp)]
                  v)
          [left-bsp right-bsp] (:children bsp)
          [v-left num-left]   (visualize-internal left-bsp v-node (inc node-num))
          [v-right num-right] (visualize-internal right-bsp v-left num-left)]
      [v-right num-right])))

(defn visualize
  "Turns the BSP into a set of strings that represents all the partitions
   graphically."
  [bi]
  (let [w (inc (:max-x bi)) ; These maxes are inclusive
        h (inc (:max-y bi))
        v (vec2d w h \.)
        v-seq (first (visualize-internal bi v 0))]
    (into [] (map #(apply str %) v-seq))))

;; To print the visualization:
#_(do (doall (map println (visualize (partition-bsp (gen-bsp 95 35))))) nil)



;;;; ROOMS ----------------------------------------------------------------------

;; These functions make rooms out of the fully partitioned BSP and then
;; connect them all together with alleyways.

(def min-room-dim-portion
  "The minimum portion of each dimension that a room can be within a
   leaf BSP node."
  0.5)

(defn random-interval-within
  "Randomly chooses a sub-interval within the inclusive min-val to max-val
   interval that is at least min-portion percent of the size.
   This function IS NOT PURE."
  [min-val max-val min-portion]
  (let [rang (- max-val min-val -1) ; Range of choices
        rand-rang (int (floor (* rang min-portion))) ; Which part is random
        base-rang (- rang rand-rang) ; Which part is fixed
        chosen-rang (+ base-rang (rand-int (inc rand-rang))) ; Our randomly chosen portion
        max-start-rang (- rang chosen-rang -1) ; Where our range starts
        start-rang (rand-int max-start-rang)]; Randomly chosen start offset from min
    #_(println rang rand-rang base-rang chosen-rang max-start-rang start-rang)
    [(+ min-val start-rang) (+ min-val start-rang chosen-rang -1)]
    ))

;; Test the above
#_(sort (reduce conj #{} (map (fn [x] (random-interval-within 0 5 0.5)) (range 0 100))))


(defn make-room
  "Creates a room that will fit into the specified BSP area, randomly
   sized and located within its [min-x min-y max-x max-y] boundaries."
  [bi]
  (let [[room-min-x room-max-x] (random-interval-within (:min-x bi) (:max-x bi) min-room-dim-portion)
        [room-min-y room-max-y] (random-interval-within (:min-y bi) (:max-y bi) min-room-dim-portion)]
    (assoc bi :room {:min-x room-min-x :max-x room-max-x :min-y room-min-y :max-y room-max-y})))

;; Test the above
#_(make-room (gen-bsp 10 10))

(defn add-rooms
  "Adds a randomly sized room to each leaf BSP node."
  [bi]
  (if (<= (count (:children bi)) 0)
    ;; We're a leaf, so make a room
    (make-room bi)
    ;; We're not a leaf, so don't make a room
    (assoc bi :children (into [] (map #'add-rooms (:children bi))))))

;; Test the above
#_(add-rooms (gen-bsp 10 10))
#_(add-rooms (partition-bsp (gen-bsp (* min-dim 4) (* min-dim 4))))

(defn visualize-rooms-internal
  "Recursively fills in the 2d vector of characters with dots representing the rooms
   of the BSP."
  [bsp v node-num]
  (if (nil? bsp)
    ;; Terminal case - no bsp
    [v node-num]
    ;; Recursive case - a bsp
    (let [node-char (char (+ (int \A) node-num))
          room (:room bsp)
          v-node (if (some? room) ; Check if nil
                   (map2d-indexed 
                      (constantly node-char)
                      [(:min-x room) (:min-y room) (:max-x room) (:max-y room)]
                      v)
                   v)
          [left-bsp right-bsp] (:children bsp)
          [v-left num-left]   (visualize-rooms-internal left-bsp v-node (inc node-num))
          [v-right num-right] (visualize-rooms-internal right-bsp v-left num-left)]
      [v-right num-right])))


(defn visualize-rooms1
  "Turns the BSP into a set of strings that represents all the rooms
   graphically."
  [bi]
  (let [w (inc (:max-x bi)) ; These maxes are inclusive
        h (inc (:max-y bi))
        v (vec2d w h \ )
        v-seq (first (visualize-rooms-internal bi v 0))]
    [(into [] (map #(apply str %) v-seq)) (into2d [] v-seq)]))

(defn visualize-rooms
  "Turns the BSP into a set of strings that represents all the rooms
   graphically."
  [bi]
  (first (visualize-rooms1 bi)))

;; Test the above
#_(do (doall (map println (visualize-rooms (add-rooms (partition-bsp (gen-bsp (* min-dim 4) (* min-dim 4))))))))

#_(use 'clojure.pprint)
#_(pprint (def y (partition-bsp (gen-bsp (* min-dim 4) (* min-dim 4)))))
#_(pprint (def x (add-rooms y)))


;;;; CORRIDORS ---------------------------------------------------------------

;; This section of the code augments the map with corridors.
;; Our simple corridor has five pieces of data:
;;   start/end x/y coordinate
;;   horiztonal or vertical first
;; So, each corridor is at most two line segments, one moving horizontal and
;; one moving vertical.
;; The start coordinate is always <= the end coordinate

(defn rand-int-incl
  "Random number between the two integers inclusive of both of them.
   Assert: a < b."
  [a b]
  (+ a (rand-int (- b a -1))))

(defn add-corridor
  "Adds a corridor between the two child BSPs."
  [bi]
  (if (zero? (count (:children bi)))
    ;; No children, hence no corridor
    bi
    ;; TODO: Pick a location in one of the child rooms recursively
    (let [[left-bsp right-bsp] (:children bi)
          left-room (:room left-bsp)
          right-room (:room right-bsp)
          start-x (rand-int-incl (:min-x left-room) (:max-x left-room))
          start-y (rand-int-incl (:min-y left-room) (:max-y left-room))
          end-x   (rand-int-incl (:min-x right-room) (:max-x right-room))
          end-y   (rand-int-incl (:min-y right-room) (:max-y right-room))
          ; start-x (min start-x1 end-x1)
          ; end-x   (max start-x1 end-x1)
          ; start-y (min start-y1 end-y1)
          ; end-y   (max start-y1 end-y1)
          horiz-first (zero? (rand-int 2))]
      (assoc bi :corridor 
        { :start-x start-x :start-y start-y
          :end-x end-x :end-y end-y :horiz-first horiz-first }))))



(defn visualize-corridor
  "Adds a corridor to this BSP's visualization (non-recursively)"
  [bi v]
  (cond
    ;; No BSP input
    (nil? bi)
    v
    ;; No corridor in the BSP input
    (nil? (:corridor bi))
    v
    ;; Draw the corridor with dots into the 2d character vector
    :else
    (let [{:keys [start-x start-y end-x end-y horiz-first]} (:corridor bi)
          ;; range needs to be ascending else it returns an empty seq
          [x1 x2] [(min start-x end-x) (max start-x end-x)]
          [y1 y2] [(min start-y end-y) (max start-y end-y)]]
      (if horiz-first
        ;; Draw the horizontal line first
        ;; See: https://stackoverflow.com/questions/22730726/idiomatic-way-to-assoc-multiple-elements-in-vector
        (let [v1 (reduce #(assoc2d %1 %2    start-y \.) v  (range x1 (inc x2)))
              v2 (reduce #(assoc2d %1 end-x %2      \.) v1 (range y1 (inc y2)))]
          v2)
        ;; Draw the vertical line first
        (let [v1 (reduce #(assoc2d %1 start-x %2     \.) v  (range y1 (inc y2)))
              v2 (reduce #(assoc2d %1 %2       end-y \.) v1 (range x1 (inc x2)))]
          v2)))))

;; Test the above
#_(pprint (def x (partition-bsp (gen-bsp (* min-dim 4) (* min-dim 4)))))
;; Repeat above until you have a BSP with exactly two levels (one set of children)
#_(pprint (def y (add-corridor (add-rooms x))))
#_(do (doall (map println (visualize-corridor y (second (visualize-rooms1 y))))) nil) 
;; Combine the above two
#_(do (def y (add-corridor (add-rooms x))) (doall (map println (visualize-corridor y (second (visualize-rooms1 y))))) nil)