(ns minimal.core
  (:import [UnityEngine Input KeyCode Camera Physics Time Camera])
  (:use arcadia.core arcadia.linear))

;; Set up our REPL
#_
(require '[arcadia.core :refer :all])
#_
(require '[minimal.core :refer :all])

(defn first-callback [o]
  (arcadia.core/log "Hello, Arcadia"))

;; Hook our first callback
#_
(hook+ (first (objects-named "object-1")) :update #'minimal.core/first-callback)

;; Move our object up a little bit when the Up key is held down.
;; See: https://docs.unity3d.com/ScriptReference/Input.GetKey.html
(defn move-when-up-pressed [o]
  (when (Input/GetKey KeyCode/UpArrow)
    (arcadia.core/log "Up pushed")))

;; Hook our move callback
#_
(hook+ (first (objects-named "object-1")) :update #'minimal.core/move-when-up-pressed)

;; Log a message when arrow keys are first pushed
(defn move-when-arrow-pressed [o]
  (when (Input/GetKeyDown KeyCode/UpArrow)
    (arcadia.core/log "Up pushed"))
  (when (Input/GetKeyDown KeyCode/DownArrow)
    (arcadia.core/log "Down pushed"))
  (when (Input/GetKeyDown KeyCode/RightArrow)
    (arcadia.core/log "Right pushed"))
  (when (Input/GetKeyDown KeyCode/LeftArrow)
    (arcadia.core/log "Left pushed")))

;; Hook our new move callback
#_
(hook+ (first (objects-named "object-1")) :update #'minimal.core/move-when-arrow-pressed)

