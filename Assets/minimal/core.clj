(ns minimal.core
  (:import [UnityEngine Input KeyCode Camera Physics Time Camera Resources Vector3 Quaternion])
  (:require [aiband.core :as ai])
  (:use arcadia.core arcadia.linear #_aiband.core))


;; Set up our REPL
#_(require '[arcadia.core :refer :all])
#_(require '[minimal.core :refer :all :reload true])

(defn first-callback [o]
  (arcadia.core/log "Hello, Arcadia"))

;; Hook our first callback
#_(hook+ (first (objects-named "object-1")) :update #'minimal.core/first-callback)

;; Move our object up a little bit when the Up key is held down.
;; See: https://docs.unity3d.com/ScriptReference/Input.GetKey.html
(defn move-when-up-pressed [o]
  (when (Input/GetKey KeyCode/UpArrow)
    (arcadia.core/log "Up pushed")))

;; Hook our move callback
#_(hook+ (first (objects-named "object-1")) :update #'minimal.core/move-when-up-pressed)

;; Log a message when arrow keys are first pushed
(defn move-when-arrow-pressed [o]
  (when (Input/GetKeyDown KeyCode/UpArrow)
    (arcadia.core/log "Up pushed"))
  (when (Input/GetKeyDown KeyCode/DownArrow)
    (arcadia.core/log "Down pushed"))
  (when (Input/GetKeyDown KeyCode/RightArrow)
    (arcadia.core/log "Right pushed"))
  (when (Input/GetKeyDown KeyCode/LeftArrow)
    (arcadia.core/log "Left pushed"))
  (let [dx1 (if (Input/GetKeyDown KeyCode/LeftArrow) -1 0)
        dx2 (if (Input/GetKeyDown KeyCode/RightArrow) 1 0)
        dy1 (if (Input/GetKeyDown KeyCode/UpArrow) -1 0)
        dy2 (if (Input/GetKeyDown KeyCode/DownArrow) 1 0)
        dx (+ dx1 dx2)
        dy (+ dy1 dy2)]
    (when (not (and (zero? dx) (zero? dy)))
      (arcadia.core/log "Total move delta:" dx dy)
      (arcadia.core/log (ai/update-game! ai/player-move dx dy)))))
        

;; Hook our new move callback
#_(hook+ (first (objects-named "object-1")) :update #'minimal.core/move-when-arrow-pressed)

(defn create-thing 
  "name - a string. location - a Vector3.
   Loads a prefab from Assets/Resources and instantiates it at the specified location."
  [name location]
  (let [prefab (Resources/Load name)
        thing (UnityEngine.Object/Instantiate prefab location Quaternion/identity)]
    (log thing)))

;; Set up our game. Call this once when the game is run.
;; We assume it's set up as a listener for Awake message on a Startup object
;; in the scene which doesn't otherwise do anything.
(defn game-startup
  "Game startup/setup routine"
  [o]
  (arcadia.core/log "Game startup")
  (create-thing "TileFloor" #unity/Vector3 [1.0 2.0 0.0])
  (create-thing "TileWall" #unity/Vector3 [4.0 0.0 0.0])
  )

#_(hook+ (first (objects-named "Startup")) :awake #'minimal.core/game-startup)
