# Kestrel — plan

Target: Minecraft Java 26.3, Fabric Loader 0.19.5+, Fabric API, Java 25. Client side only.
Goal: one jar that does the job of several performance mods, with a switch and a number for everything.

## What it replaces (in spirit)
| Mod family | Kestrel module |
|---|---|
| Entity Culling / More Culling | entity + block entity distance culling, nearest-first budgets, per-mob overrides |
| Particle Core / Particle Rain | particle budget (nearest first), distance limit, behind-camera cull, explosion effect cap |
| Dynamic FPS / FPS Reducer | FPS cap in menus and when the window is unfocused |
| Sodium Extra / Better Performance presets | vanilla option presets with backup/restore, first-run auto tune |
| Lithium-ish (client side part) | far TNT / item / xp / falling block tick throttle |
| Sound limiters | explosion sound cap per tick |
| Animation throttles | animated texture throttle |
| Debug HUD mods | stats HUD, 1% lows, heap, cutoff distances, in-screen benchmark |
| Thread tuners | render thread up, server / worker threads down |
| Iris-friendly culling | shader mode: budgets x70%, shadow pass distance limit |

## Principles
1. Every module has its own switch and numbers; the master switch in the Presets tab turns the modules off for A/B tests.
2. Nothing close to the player is ever touched (12 block safe radius, 6 for particles).
3. Cuts must not flicker: pick by entity id / particle identity or by a smoothed distance cutoff, never random per frame.
4. A hook that does not match the game fails alone and says so in latest.log; it never takes the game down.
5. Prefer MixinExtras @Local over spelling out version-specific signatures; look up uncertain APIs by name at runtime.

## Modules
| # | Module | Hook | Status |
|---|--------|------|--------|
| 1 | Entity culling by distance, size aware, per-mob overrides | EntityRenderer.shouldRender | done |
| 2 | Entity budget (nearest first) | same | done |
| 3 | TNT LOD, item/xp/falling block LOD | same | done |
| 4 | Block entity culling + budget | BlockEntityRenderDispatcher.tryExtractRenderState | done |
| 5 | Particle limiter (budget, distance, behind camera) | SingleQuadParticle.extract + VarHandle position read | done |
| 6 | Explosion effect cap | ClientLevel.trackExplosionEffects | done |
| 7 | Explosion sound cap | ClientLevel.playLocalSound | done |
| 8 | Far entity tick throttle | ClientLevel.tickNonPassenger | done |
| 9 | Animated texture throttle | TextureAtlas.cycleAnimationFrames | done |
| 10 | FPS governor (scales everything with FPS) | Minecraft.runTick | done |
| 11 | FPS caps in menus / unfocused | runTick | done |
| 12 | Thread priorities | Thread API | done |
| 13 | Adaptive render distance (opt-in) | Options via reflection | done |
| 14 | Vanilla option presets + restore, first-run auto tune | Options via reflection | done |
| 15 | HUD (fps, 1% low, ms, LOD, skipped, heap, cutoffs), F8 | Fabric HUD | done |
| 16 | Settings UI, in-screen benchmark, F9 preset cycle and key mappings | Screen mixins / key mappings | done |
| 17 | GC on leaving a world | Fabric connection event | done |
| 18 | Settings screen (tabs, +/- buttons, tooltips, per-mob distances, Info tab), K key, button on title / pause / options screens | Screen subclass + screen mixins | done |
| 19 | Shader pack mode (Iris): smaller budgets, shadow pass entity / block entity culling | Iris API by name | done |

## Next (not started)
- Real occlusion test for entities / block entities (caves, walls)
- Sound engine channel limit for everything, not only explosions
- Config screen
- Chunk upload / meshing throttle during mass block changes

## How to verify a build
1. latest.log: `[Kestrel] hook active:` lines. Settings screen (K) > Info shows every hook, thread priorities, cutoffs.
2. Same world: Presets > master switch off, Bench 20 s; master on, Bench 20 s. Results are printed in chat and listed in Info.
3. TNT cannon / big chest room / mob farm, with and without the potato preset, with and without a shader pack.
