(ns aiband.bsp
  (:require [aiband.v2d :refer :all :reload true]))

(def min-dim
  "Minimum dimension for a partitioning"
  7)

(def min-area
  "Minimum area for a partitioning"
  (* 8 8))

(defn make-bsp
  [min-x min-y max-x max-y children]
  { :min-x min-x :min-y min-y :max-x max-x :max-y max-y
    :children (into [] children) })

(defn split
  "Splits a bsp randomly either horizontally or vertically.
   Returns list of two new bsps. Always leaves a column/row
   of buffer between the two. As such, make sure that the
   width or height of this input bsp is at least 3."
  [bi] ; bsp-in
  (let [vs (= 1 (rand-int 2)) ; vertical split
        min-d (if vs (:min-x bi) (:min-y bi))
        max-d (if vs (:max-x bi) (:max-y bi))
        d-split (+ min-d 1 (rand-int (- max-d min-d 1)))]
    ;; First our lesser half, then our greater half
    [(make-bsp (:min-x bi) (:min-y bi)
               (if vs (- d-split 1) (:max-x bi))
               (if vs (:max-y bi) (- d-split 1)) [])
     (make-bsp (if vs (+ d-split 1) (:min-x bi))
               (if vs (:min-y bi) (+ d-split 1))
               (:max-x bi) (:max-y bi) [])]))

(defn visualize
  [bi]
  (let [w (:max-x bi)
        h (:max-y bi)
        v (vec2d w h \.)]
    v))

(defn partition-bsp
  "Returns the input bsp with children added, or
   if it's too small to partition, unchagned."
  [bsp-in]
  ;; The -1 is because if it's exactly one column/row, it's still that wide
  (let [w (- (:max-x bsp-in) (:min-x bsp-in) -1)
        h (- (:max-y bsp-in) (:min-y bsp-in) -1)]
    (if (and (> w min-dim)
             (> h min-dim)
             (> (* w h) min-area))
      ;; Partition this one and its (new) children
      (assoc bsp-in :children (mapv partition-bsp (split bsp-in)))
      ;; Don't partition
      bsp-in)))

;; Returns a structure like this:
;; { :min-x :min-y :max-x :max-y :children [...] }
(defn gen-bsp
  [w h]
  { :min-x 0 :max-x w :min-y 0 :max-y h :children () })

