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
   * Alternatively, `rlwrap -r ruby Arcadia/Editor/repl-client.rb` seems to work better
     for the REPL but apparently is deprecated (get rlwrap from homebrew)
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

4. Tell Arcadia that our `.clj` file has changed by using this command in the REPL: 
`(require 'minimal.core :reload)`. The Arcadia team will probably have a file watcher
in the future that may obviate this step.

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


# Miscellaneous Notes

To see all hookable things in Arcadia/Unity:

```clojure
user=> (use 'arcadia.core)
nil

user=> hook-types
{
 :awake AwakeHook
 :fixed-update FixedUpdateHook
 :late-update LateUpdateHook
 :on-animator-ik OnAnimatorIKHook
 :on-animator-move OnAnimatorMoveHook
 :on-application-focus OnApplicationFocusHook
 :on-application-pause OnApplicationPauseHook
 :on-application-quit OnApplicationQuitHook
 :on-audio-filter-read OnAudioFilterReadHook
 :on-became-invisible OnBecameInvisibleHook
 :on-became-visible OnBecameVisibleHook
 :on-begin-drag OnBeginDragHook
 :on-cancel OnCancelHook
 :on-collision-enter OnCollisionEnterHook
 :on-collision-enter2d OnCollisionEnter2DHook
 :on-collision-exit OnCollisionExitHook
 :on-collision-exit2d OnCollisionExit2DHook
 :on-collision-stay OnCollisionStayHook
 :on-collision-stay2d OnCollisionStay2DHook
 :on-connected-to-server OnConnectedToServerHook
 :on-controller-collider-hit OnControllerColliderHitHook
 :on-deselect OnDeselectHook
 :on-destroy OnDestroyHook
 :on-disable OnDisableHook
 :on-disconnected-from-server OnDisconnectedFromServerHook
 :on-drag OnDragHook
 :on-draw-gizmos OnDrawGizmosHook
 :on-draw-gizmos-selected OnDrawGizmosSelectedHook
 :on-drop OnDropHook
 :on-enable OnEnableHook
 :on-end-drag OnEndDragHook
 :on-failed-to-connect OnFailedToConnectHook
 :on-failed-to-connect-to-master-server OnFailedToConnectToMasterServerHook
 :on-gui OnGUIHook
 :on-initialize-potential-drag OnInitializePotentialDragHook
 :on-joint-break OnJointBreakHook
 :on-level-was-loaded OnLevelWasLoadedHook
 :on-master-server-event OnMasterServerEventHook
 :on-mouse-down OnMouseDownHook
 :on-mouse-drag OnMouseDragHook
 :on-mouse-enter OnMouseEnterHook
 :on-mouse-exit OnMouseExitHook
 :on-mouse-over OnMouseOverHook
 :on-mouse-up OnMouseUpHook
 :on-mouse-up-as-button OnMouseUpAsButtonHook
 :on-move OnMoveHook
 :on-network-instantiate OnNetworkInstantiateHook
 :on-particle-collision OnParticleCollisionHook
 :on-player-connected OnPlayerConnectedHook
 :on-player-disconnected OnPlayerDisconnectedHook
 :on-pointer-click OnPointerClickHook
 :on-pointer-down OnPointerDownHook
 :on-pointer-enter OnPointerEnterHook
 :on-pointer-exit OnPointerExitHook
 :on-pointer-up OnPointerUpHook
 :on-post-render OnPostRenderHook
 :on-pre-cull OnPreCullHook
 :on-pre-render OnPreRenderHook
 :on-render-image OnRenderImageHook
 :on-render-object OnRenderObjectHook
 :on-scroll OnScrollHook
 :on-select OnSelectHook
 :on-serialize-network-view OnSerializeNetworkViewHook
 :on-server-initialized OnServerInitializedHook
 :on-submit OnSubmitHook
 :on-trigger-enter OnTriggerEnterHook
 :on-trigger-enter2d OnTriggerEnter2DHook
 :on-trigger-exit OnTriggerExitHook
 :on-trigger-exit2d OnTriggerExit2DHook
 :on-trigger-stay OnTriggerStayHook
 :on-trigger-stay2d OnTriggerStay2DHook
 :on-update-selected OnUpdateSelectedHook
 :on-validate OnValidateHook
 :on-will-render-object OnWillRenderObjectHook
 :reset ResetHook
 :start StartHook
 :update UpdateHook
}
```

# Credits

* [`dg_features32.png`](http://pousse.rapiere.free.fr/tome/tiles/AngbandTk/tome-angbandtkdungeontiles.htm)


