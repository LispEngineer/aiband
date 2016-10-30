# Arcadia's Hello World

Douglas P. Fields, Jr.  
symbolics at lisp.engineer

1. Create a new, blank Unity 2D game, using Unity 5.4.0f3.
2. Unity menu: Edit -> Project Settings -> Player
   * Resolution & Presentation -> Run in Background* -> CHECK
   * Other Settings -> Api Compatibility Level -> .NET 2.0
3. Clone Arcadia Github into `Assets` directory and let Unity load it (be patient)
4. `cd Assets/Arcadia/Infrastructure` and run the `./repl` program there, then
   * `(require '[arcadia.core :refer :all])`
   * Leave the REPL open for later
5. Drag a (random, small) image into your Unity `Assets` pane/window
6. Drag that image from the `Assets` window into the `Scene` pane/window, on top of the camera icon.
   * Confirm that the `Hierarchy` pane shows your image (it will be a `Sprite` now)
7. Rename that in the `Hierarchy` window to `object-1`
8. Create a new Clojure file in pathname `Assets/minimal/core.clj` with the contents below.
9. In the REPL, run this: `(require '[minimal.core :refer :all])` to load it into the REPL

10. Now the magic happens: 
We're going to have the function `first-callback` get called 
every frame by linking (hooking) it to the `object-1` object 
in the scene. This can only be done in the REPL for now, but the
hook will show up in the Scene in the inspector. This will, for
now, just spam the Unity `Console` window with boring messages.

10. We can access game objects by name using the Arcadia function `objects-named`. This returns a list. 
REPL: `user=> (objects-named "object-1")` ->
`(#unity/GameObject -22008)`

11. Add the hook for the function in our `minimal/core.clj` file to the sprite we named `object-1` in the REPL:
`user=> (hook+ (first (objects-named "object-1")) :update #'minimal.core/first-callback)` ->
`#unity/GameObject -22008`

12. Confirm this by clicking on `object-1` in the Unity `Hierarchy` window and note in the `Inspector` that it says `Update Hook (Script)` with `#'minimal.core/first-callback`.

13. Make sure the `Console` window is showing. Click `Play` icon in Unity. Be patient. After a short time, your first Arcadia app will start running and the `Console` window will start showing a lot of messages like: `Hello, Arcadia` from `UnityEngine.Debug:Log(Object)`.

14. Save your scene (whatever name you want). Quit Unity. Restart Unity and load your project. Hit Play. Everything should still work.


## `Assets/minimal/core.clj` v1

```clojure
(ns minimal.core
  (:use arcadia.core arcadia.linear))

(defn first-callback [o]
  (arcadia.core/log "Hello, Arcadia"))
```

# Getting Input

1. We need to enhance the namespace to use some Unity objects. Modify the `ns` in the top as per the `minimal/core.clj` v2 code below.

2. Write a function which reads keys and prints something.
   * `Input/GetKey` returns true every `update` when a key is held down.
   * `Input/GetKeyDown` returns true only the first `update` a key has been pressed
   * See code below for `move-when-arrow-pressed`

3. Remove our old Hello World hook to `first-callback` in the Unity GUI by clicking the gear icon next to `Update Hook (Script)` when `object-1` is showing in the `Inspector` window. Choose `Remove Component`.

4. (TODO... How do we get the REPL to reload our core.clj?)

5. In the REPL, add our new hook: `(hook+ (first (objects-named "object-1")) :update #'minimal.core/move-when-arrow-pressed)`
   * Confirm this by checking the `Inspector` for `object-1` in Unity

6. Run the game, and tap & hold various arrow keys.


## `Assets/minimal/core.clj` v2

```clojure
(ns minimal.core
  (:import [UnityEngine Input KeyCode Camera Physics Time Camera])
  (:use arcadia.core arcadia.linear))

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
```
   
# Using Input

TODO: Use the input to modify the location of the object.

# Programmatically Controlling Game Objects

TODO: Generate and remove game objects, and change the sprite they display, etc.