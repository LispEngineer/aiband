;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike

;;;; Field of view / Line of sight module

;; Calculates line of sight using a single parameter, distance.
;; As we are on a map where diagonal movement is the same as lateral,
;; you can see that distance in a square around you.
;; Distance 1 is a 3x3 square, Distance 2 is a 5x5 square, etc.
;; (Distance * 2 + 1) ^ 2.
;;
;; We calculate the edges of the square, and then for each edge
;; coordinate, we calculate all the points from your current point
;; out to that edge, using a Bresenham algorithm.
;; (https://en.wikipedia.org/wiki/Bresenham%27s_line_algorithm)
;;
;; Finally, and inefficiently, we traverse each of these "rays"
;; outward revealing terrain until we reach a coordinate that
;; blocks further visibility, and then we stop.
;; This is somewhat inefficient as the number of coordinates traversed
;; is now proportional to (perimeter * distance) =
;; (distance * 2 + 1) * 4 - 2) * distance
;; (8 * distance + 2) * distance
;; 8 * distance ^ 2 + 2 * distance
;; so in the worst we traverse every possibly visible coordinate ~8 times.

(ns aiband.fov
  (:require [aiband.v2d :refer :all :reload true]
            [aiband.clrjvm :refer :all :reload true]))

(defn box-coords
  "Gets a seq of [x y] coordinates around [0 0] representing the
   perimeter of a box 'distance' units from the center. These are not
   in any particular order."
  [dist]
  (if (<= dist 0)
    ;; Degenerate case of just the origin
    [[0 0]]
    (let [[start-x end-x] [(- dist) dist]
          [start-y end-y] [(- dist) dist]]
      (concat
        ;; Bottom of perimeter starting at left
        (map (fn [x] [x start-y]) (range start-x (inc end-x)))
        ;; Top of perimiter starting at left
        (map (fn [x] [x end-y]) (range start-x (inc end-x)))
        ;; Left of perimeter - don't duplicate top or bottom
        (map (fn [y] [start-x y]) (range (inc start-y) end-y))
        ;; Right of perimeter
        (map (fn [y] [end-x y]) (range (inc start-y) end-y))))))

;; https://github.com/jackschaedler/goya/blob/master/src/cljs/goya/components/bresenham.cljs
#_(defn bresenham [x0 y0 x1 y1]
  (let [len-x (js/Math.abs (- x0 x1))
        len-y (js/Math.abs (- y0 y1))
        is-steep (> len-y len-x)]
    (let [[x0 y0 x1 y1] (if is-steep [y0 x0 y1 x1] [x0 y0 x1 y1])]
      (let [[x0 y0 x1 y1] (if (> x0 x1) [x1 y1 x0 y0] [x0 y0 x1 y1])]
        (let [delta-x (- x1 x0)
              delta-y (js/Math.abs (- y0 y1))
              y-step (if (< y0 y1) 1 -1)]
          (loop [x x0
                 y y0
                 error (js/Math.floor (/ delta-x 2))
                 pixels (if is-steep [[y x]] [[x y]])]
            (if (> x x1)
              pixels
              (if (< error delta-y)
                (recur (inc x)
                       (+ y y-step)
                       (+ error (- delta-x delta-y))
                       (if is-steep (conj pixels [y x]) (conj pixels [x y])))
                (recur (inc x)
                       y
                       (- error delta-y)
                       (if is-steep (conj pixels [y x]) (conj pixels [x y]))
                       )))))))))

;; Based on code here:
;; https://github.com/jackschaedler/goya/blob/master/src/cljs/goya/components/bresenham.cljs
;; This gives a less symmetric line, I feel.
(defn origin-line'
  "Gives a seq of [x y] coordinates, including the origin, from [0 0] to
   the provided coordinate, which must be in the first octant
   (y <= x, x > 0, y > 0)."
  [[to-x to-y]]
  (loop [x 0 y 0
         error (floor (/ to-x 2))
         coords []] ; Our return value
    (if (> x to-x)
      ;; We're done
      coords
      ;; Pick a Y coord per error and draw that pixel
      (if (< error to-y)
        ;; Go up one in Y next time
        (recur (inc x) (inc y)
          (+ error (- to-x to-y))
          (conj coords [x y]))
        ;; Stay at Y the next time
        (recur (inc x) y
          (- error to-y)
          (conj coords [x y]))))))

;; Based on algorithm here:
;; https://www.cs.drexel.edu/~introcs/Fa11/notes/08.3_MoreGraphics/Bresenham.html?CurrentSlide=4
;; This seems to give a more pleasing line.
; var dy = y2-y1
; var dx = x2-x1
; var d = 2*dy - dx
; var x = x1
; var y = y1
; while (x <= x2) {
;   Draw pixel at (x,y)
;   x++
;   if( d<0 )
;     d += dy + dy
;   else {
;     d += 2*(dy-dx)
;     y++
;   }
; }
;; TODO: MEMOIZE ME
(defn origin-line-first-octant
  "Gives a seq of [x y] coordinates, including the origin, from [0 0] to
   the provided coordinate, which must be in the first octant
   (y <= x, x > 0, y > 0)."
  [[to-x to-y]]
  (loop [x 0 y 0
         d (- (* 2 to-y) to-x)
         coords []] ; Our return value
    (if (> x to-x)
      ;; We're done
      coords
      ;; Pick a Y coord per error and draw that pixel
      (if (< d 0)
        ;; Go up one in Y next time
        (recur (inc x) y
          (+ d to-y to-y)
          (conj coords [x y]))
        ;; Stay at Y the next time
        (recur (inc x) (inc y)
          (+ d (* 2 (- to-y to-x)))
          (conj coords [x y]))))))

;; TODO: MEMOIZE ME
(defn origin-line
  "Handles coordinates of a line to any coordinate from the origin, 
   regardless of octant, by reflecting it (possibly several times)
   to the first octant."
  [[x y :as coords]]
  (cond
    ;; First octant
    (and (>= x 0) (>= y 0) (<= y x))
    (origin-line-first-octant coords)
    ;; Second octant - swap x & y coords
    (and (>= x 0) (>= y 0)) ; and y > x
    (mapv (fn [[x y]] [y x]) (origin-line [y x]))
    ;; second or third QUADRANT
    (< x 0)
    (mapv (fn [[x y]] [(- x) y]) (origin-line [(- x) y]))
    ;; fourth QUADRANT
    :else ; assert (< y 0)
    (mapv (fn [[x y]] [x (- y)]) (origin-line [x (- y)]))
    ))

(defn line
  "Gives coordinates of all points between the specified coordinates.
   Handles it by translating the line to have a
   first coordinate of [0 0] and then
   drawing an origin-based line, and then translating it back."
  [[x1 y1] [x2 y2]]
  (mapv (fn [[x y]] [(+ x x1) (+ y y1)])
    (origin-line [(- x2 x1) (- y2 y1)])))

;; TODO: MEMOIZE ME
(defn origin-los-rays
  "Returns a seq of a seq containing all the line of sight ray 
   coordinates (2-vectors) from
   the origin out to the specified distance.
   You can take the origin out of this by doing (mapv rest (origin-los-rays dist))."
  [dist]
  (mapv (fn [coord] (origin-line coord)) (box-coords dist)))

(defn los-rays
  "Returns a seq of a seq containing all the line of sight ray 
   coordinates (2-vectors) from
   the specified coordinate out to the specified distance.
   You can take the original coordinate out of this by doing 
   (mapv rest <result>) on the return value of this function."
  [[o-x o-y] dist]
  (mapv
    (fn [ray] (mapv (fn [[x y]] [(+ x o-x) (+ y o-y)]) ray))
    (origin-los-rays dist)))
