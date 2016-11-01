(ns minimal.core
  (:import [UnityEngine Input KeyCode Camera Physics Time Camera Resources Vector3 Quaternion])
  (:require [aiband.core :as ai])
  (:use arcadia.core arcadia.linear #_aiband.core))


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
        

(defn create-thing
  "name - a string. location - a Vector3.
   Loads a prefab from Assets/Resources and instantiates it at the specified location."
  [name location]
  (let [prefab (Resources/Load name)
        thing (UnityEngine.Object/Instantiate prefab location Quaternion/identity)]
    #_(arcadia.core/log thing)
    thing))

(defn terrain-tile
  "Converts a terrain symbol into a Prefab Tile name"
  [t]
  (cond
    (= t :wall)  "TileWall"
    (= t :floor) "TileFloor"
    :else        "TileRock"))

;; Set up our game. Call this once when the game is run.
;; We assume it's set up as a listener for Awake message on a Startup object
;; in the scene which doesn't otherwise do anything.
(defn game-startup
  "Game startup/setup routine"
  [o]
  (arcadia.core/log "Game startup")
  (let [terrain (:terrain (:level @ai/game-state))
        tgo (GameObject. "Terrain")  ; A Unity GameObject that will hold our other Terrain GOs
        tt  (. tgo transform)] ; The transform of the above
    (arcadia.core/log "Terrain" terrain)
    ;; Iterate over the 2D vector including indices
    (doseq [[y row] (map list (range) terrain)]
      (doseq [[x t] (map list (range) row)]
        (let [go (create-thing (terrain-tile t) (arcadia.linear/v3 x y 0.0))]
          ;; Set our name
          (set! (. go name) (str "terrain-" x "," y))
          ;; Put it in the Terrain holder
          ;; https://unity3d.com/learn/tutorials/projects/2d-roguelike-tutorial/writing-board-manager?playlist=17150
          (. (. go transform) SetParent tt)
          (arcadia.core/log "Created" x y t go))))))


;; Arcadia REPL --------------------------------------------------------------------------

#_
(do
  ;; Set up our REPL and hooks
  (require '[arcadia.core :refer :all])
  (require '[minimal.core :refer :all :reload true])
  (in-ns 'minimal.core)

  ;; Hook the move and awake callbacks
  ;; Awake works great for playing in the Unity Editor, but not in standalone Mac game
  (hook+ (first (objects-named "Startup")) :awake #'minimal.core/game-startup)
  ;; Hook the :start for standalone.
  ;; Per Ramsey Nasser, "we do funky things with awake that might get in the way of user code"
  (hook+ (first (objects-named "Startup")) :start #'minimal.core/game-startup)
  (hook+ (first (objects-named "Startup")) :update #'minimal.core/move-when-arrow-pressed)
  )
