;; Copyright 2016 Douglas P. Fields, Jr.
;; symbolics _at_ lisp.engineer
;; https://symbolics.lisp.engineer/
;; https://twitter.com/LispEngineer
;; https://github.com/LispEngineer

;;;; CLR/JVM interoperability code

(ns aiband.clrjvm)

(defn class-exists?
  "Does the specified class (or var) exist? Example:
   (class-exists 'java.lang.Math) or (class-exists 'System.Math)"
  [sym]
  (try
    (do
      (resolve sym)
      true)
    (catch Exception e false))) ; This works only because Exception is resolved to different names in CLR and JVM

(def platform
  "Which platform are we on? :clj (JVM) and :cljr (CLR) for now."
  (cond
   (class-exists? 'java.lang.Math) :clj
   (class-exists? 'System.Math)    :cljr
   :else                           :unknown))

(defn ceil
  "Ceiling function in math"
  [v]
  (case platform
    :clj  (eval `(java.lang.Math/ceil ~v))
    :cljr (eval `(System.Math/Ceiling (double ~v)))
    nil))

(defn floor
  "Floor function in math"
  [v]
  (case platform
    :clj  (eval `(java.lang.Math/floor ~v))
    :cljr (eval `(System.Math/Floor (double ~v)))
    nil))