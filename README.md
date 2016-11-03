# Aiband

Douglas P. Fields, Jr.  
symbolics at lisp.engineer  
https://symbolics.lisp.engineer/

*Artificial Intelligence (ang)band*

A roguelike game where the player is an Artificial Intelligence
residing in a computer, usually attached to a drone (robot) or
possibly running several drones.


# Infrastructure Notes

* Unity 5.4.0f3
* Arcadia (Unity/Clojure interop)
* ClojureCLR 1.7.0


# Unity Configuration

## Assets

* `Resources` - Unity prefabs are here

## Scenes

## Sprites

Took a texture and split it into 32x32 sprites.

* Slice by Grid Size: 32x32
* Pivot: Bottom Left

Pixels per unit is 100. Combine this with a Prefab with an X/Y scale of 3.2
and the texture will take up exactly 1x1 Unity square.

## 2D Sorting Layers

* Terrain - lowest
* Items - items on top of terrain
* Mobs - "Mobile Objects" a.k.a. monsters, on top of tiems
* Player - not sure what this layer could be used for

## 




# Miscellaneous Notes

## Arcadia Issues

Don't hook `:awake`. Per Ramsey Nasser, "we do funky things with awake that might get 
in the way of user code." Hook `:start` instead.

## Main Camera

Game is _currently_ designed to be played in a 16x9 window.

Camera is located at (8, 4.5) and shows a view 16x9.

Bottom left part of screen is game coordinate (0,0) and top right is (16,9).

## Updating Unity Transform

You must set the transform.position to a whole new Vec3 `(v3 x y z)`
rather than just setting individual fields of the existing one.

## Arcadia/Unity

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

What namespaces Arcadia sees:

```
user=> (require 'arcadia.internal.editor-interop)
nil

user=> (arcadia.internal.editor-interop/all-user-namespaces-symbols)
(aiband.core minimal.core)
```

# Credits

* [`dg_features32.png`](http://pousse.rapiere.free.fr/tome/tiles/AngbandTk/tome-angbandtkdungeontiles.htm)


