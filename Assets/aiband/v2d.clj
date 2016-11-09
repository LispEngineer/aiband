;;;; Douglas P. Fields, Jr.
;;;; symbolics at lisp.engineer - https://symbolics.lisp.engineer/
;;;; Aiband - The Artificial Intelligence Roguelike

;; Two Dimensional Vector helpers
(ns aiband.v2d)

;; Load our module into the REPL
#_(require '[aiband.v2d :refer :all :reload true])
#_(in-ns 'aiband.v2d)

(defn vec2d
  "Creates a 2D (ragged, but square) vector with the specified size and
   starting value for all positions. Row index is first."
  ([[w h] initial] (vec2d w h initial))
  ([w h initial]
   (vec (replicate h (vec (replicate w initial))))))

(defn get2d
  "Gets the specified [x y] coordinates in a (ragged) 2d vector or other 'get'able thing.
   First deindex is row (y), second index is column (x), but the acceptable parameters
   are 'x y' or [x y]."
  ([v2d x y] (get-in v2d [y x]))
  ([v2d [x y]] (get2d v2d x y)))

(defn assoc2d
  "Sets the specified [x y] coordinate of the (ragged) 2D vector first
   indexed by row.
   (Yes, this just does assoc-in with reversing the coordinate.)"
  ([v2d [x y] newval] (assoc2d v2d x y newval))
  ([v2d x y newval] (assoc-in v2d [y x] newval)))

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


;;;; Neighbors ---------------------------------------------------------------

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

(defn any-neighbor=
  "Returns true if any neighbor of the specified (ragged but square) 2D vector location is equal to
   the specified value."
   [v2d [x y :as coords] eqval]
   (let [max-y (count v2d)
         max-x (count (first v2d))]
      #_(arcadia.core/log max-x max-y coords)
      (some #(= eqval %) 
            (map #(get2d v2d %) 
                 (neighbors-of coords [max-x max-y])))))

