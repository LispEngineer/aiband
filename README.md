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


# `Assets/minimal/core.clj`

```clojure
(ns minimal.core
  (:use arcadia.core arcadia.linear))

(defn first-callback [o]
  (arcadia.core/log "Hello, Arcadia"))
```
