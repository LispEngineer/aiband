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

(defn abs
  "Absolute value function in math"
  [v]
  (case platform
    :clj  (eval `(java.lang.Math/abs ~v))
    :cljr (eval `(System.Math/Abs ~v))
    nil))

(defn time-ms
  "Milliseconds since Jan 1, 1970, UTC."
  []
  (case platform
    :clj  (eval `(java.lang.System/currentTimeMillis))
    ;; https://stackoverflow.com/questions/5680375/asp-net-get-milliseconds-since-1-1-1970
    :cljr (eval `(- (long (/ (. (DateTime/UtcNow) Ticks) TimeSpan/TicksPerMillisecond)) 62135596800000))
    nil))

;; Test the above
; .../Assets$ mono ~/src/clojure/nostrand/bin/Debug/Nostrand.exe cli-repl
; user> (require ['aiband.clrjvm :reload true]) 
; nil
; user> (aiband.clrjvm/time-ms)                 
; 1479942231426
; user>  
;
; .../Assets$ rlwrap java -cp /opt/clojure-1.7.0/clojure-1.7.0.jar:. clojure.main
; Clojure 1.7.0
; user=> (require ['aiband.clrjvm :reload true])
; nil
; user=> (aiband.clrjvm/time-ms)
; 1479942219742
; user=> 

