;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer
;;;; Aiband - The Artificial Intelligence Roguelike

;;;; Random utilities

;; For now, these are impure. Eventually I'll want to make a full random
;; monad with properly pure random stuff.

(ns aiband.random)

(defn rand-coord-seq
  "Returns an infinitely long lazy sequence of random
   [x y] coordinates within the specified bounds of width and height."
  [w h]
  (repeatedly #(do [(rand-int w) (rand-int h)])))

;; https://en.wikipedia.org/wiki/Xorshift
; #include <stdint.h>
; uint64_t x; /* The state must be seeded with a nonzero value. */
; uint64_t xorshift64star(void) {
;   x ^= x >> 12; // a
;   x ^= x << 25; // b
;   x ^= x >> 27; // c
;   return x * UINT64_C(2685821657736338717);
; }
(defn xorshift64*
  "Implements the raw xorshift64* random algorithm. Takes the seed and returns the
   next random number and the new seed. Since Clojure doesn't have unsigned 64-bit
   integers, but does have 64-bit signed integers, the seed and the result may be
   negative. These numbers can be made positive (and equivalent to the C algorithm's
   output) by converting to BigInt adding 2^64.
   See: https://en.wikipedia.org/wiki/Xorshift"
  [x]
  (let [a (bit-xor x (unsigned-bit-shift-right x 12))
        b (bit-xor a (bit-shift-left a 25))
        c (bit-xor b (unsigned-bit-shift-right b 27))
        ;; unchecked prevents math overflow
        r (unchecked-multiply c 2685821657736338717)]
    [r c]))

; C output using initial seed of 8675309:
; .../Assets/aiband$ ./rand-c 
; 4540130931784252282
; 12621637665035066184
; 17464787638490669911
; 15716726280457592975
; 16639639037714465769
; 7492506681946718951
; 16265382938554115520
; 12476093826064089859
; 5916695321102103813
; 3040243445305702638

; user> (require ['aiband.random :reload true])                
; nil
; user> (aiband.random/xorshift64* 8675309)                    
; [4540130931784252282 291024042017346]
; user> (aiband.random/xorshift64* 291024042017346)
; [-5825106408674485432 9202446269008924136]
;; Using bc: 2^64 -5825106408674485432 = 12621637665035066184
; user> (aiband.random/xorshift64* 9202446269008924136)
; [-981956435218881705 -8451116933958960125]
;; Using bc: 2^64 -981956435218881705 = 17464787638490669911
; user> (aiband.random/xorshift64* -8451116933958960125)                   
; [-2730017793251958641 -6856013568780943205]
;; Using bc: 2^64 -2730017793251958641 = 15716726280457592975
;;;;;; CONCLUSION: Looks good, but it uses a final signed result

;; Also:
#_(map (fn [n] (if (< n 0) (+ (bigint n) (reduce * (repeat 64 2N))) (bigint n))) (aiband.random/xorshift64* 291024042017346))
; => (12621637665035066184N 9202446269008924136N)

(def two63-1 "2^63-1" 9223372036854775807)
(def two63   "2^63"   9223372036854775808)

(defn ->pos
  "Converts a random long to a positive number."
  [n]
  (bit-and n two63-1))

(defn ->double
  "Converts a random long to a double in the range [0, 1). It can actually return
   1.0, which is not good?
   user> (double (/ 9223372036854771657 9223372036854775808))
   0.999999999999999
   user> (double (/ 9223372036854771658 9223372036854775808)) 
   1.0"
  [n]
  (double (/ (->pos n) two63)))

(defn make-seed
  "Makes a random seed from the provided (long) number."
  [n']
  ;; Multiply a bunch of non-zero numbers together
  (let [n (long n')
        s (unchecked-multiply 
            (unchecked-multiply (bit-or n 1) (bit-or n 2)) 
            (unchecked-multiply (bit-or n 240) (bit-or n 3008)))]
    (if (zero? s)
      ;; Should almost never happen
      8675309
      s)))

