# Kestrel

Kestrel is a client-side performance mod for **Minecraft: Java Edition 26.3**. It gives you control over several rendering and client workload settings from one in-game menu, so you can tune performance without digging through a long list of separate options.

Performance depends on your hardware, world, resource packs, shader packs and other installed mods. Some settings reduce visual detail or update frequency to save work, so the best results will depend on your setup.

## Features

- **Entity distance controls:** reduce rendering work for distant entities and set separate distances for supported mob types. Players are excluded from the regular entity-distance setting.
- **Entity level of detail:** use simpler rendering for supported distant entities, including dropped items, XP orbs, primed TNT and falling blocks.
- **Distant entity updates:** reduce how often selected distant entities are updated on the client.
- **Particles and explosions:** control particle distance and limits, optionally skip particles behind the camera, and limit explosion effects and sounds.
- **Animated textures:** lower the update rate of animated textures such as water, lava and fire.
- **FPS governor:** adjust selected workload limits when FPS stays below your target. Optional settings can also change render distance or limit FPS in menus and when the game window is unfocused.
- **Shader compatibility:** when a compatible Iris API is available, Kestrel can adjust performance budgets while shaders are active and limit distant entity work in shadow rendering.
- **Video presets:** apply lightweight vanilla video settings, with a backup available for restoring the previous values.
- **Auto tune and performance stats:** optionally choose a lower preset on the first world join when system resources appear limited. The HUD can show FPS, 1% low, frame time and skipped-work counters.

Settings can be adjusted individually, and the master switch makes it easy to compare gameplay with Kestrel enabled or disabled.

## Requirements

- **Minecraft:** Java Edition 26.3
- **Fabric Loader:** 0.19.5 or newer
- **Fabric API:** 0.165.0+26.3 or a compatible release for Minecraft 26.3
- **Java:** 25 or newer
- **Side:** Client only

Kestrel does not need to be installed on a multiplayer server. Only the player who wants to use its client-side features needs it.

## Installation

1. Install Fabric for Minecraft Java Edition 26.3, including Fabric Loader and Fabric API.
2. Download the latest Kestrel `.jar` from the project's [Releases](../../releases) page. Choose the regular mod file, not a `-sources.jar` file.
3. Place the downloaded JAR in your Minecraft `mods` folder.
4. Start Minecraft using your Fabric 26.3 profile.

## Controls and settings

| Key or location | Action |
| --- | --- |
| `K` | Open Kestrel settings |
| `F8` | Toggle the performance HUD |
| `F9` | Cycle performance presets |
| Title, pause and options screens | Open Kestrel using the button in the top-left area |

Key bindings can be changed under **Options → Controls → Key Binds**. The settings screen includes tabs for Presets, Entities, Mobs, World, FPS and Info. Most numeric settings use `-` and `+` buttons; click a value to reset it to its default. Changes are saved as you make them.

The **Presets** tab includes a short benchmark tool for comparing performance with Kestrel enabled and disabled in the same world. Results are estimates and can vary between runs.

## Settings and backups

Kestrel stores its settings in `config/kestrel.properties`.

Before changing vanilla video options, Kestrel saves their previous values to `config/kestrel-options-backup.properties`. Use **Restore video options** in the Presets tab to restore the saved settings. Options that are missing or unsupported by the current game version are skipped.

## Troubleshooting

If a feature does not appear to work, open **Kestrel → Info** and check the hook status. A hook marked `not seen yet` may simply not have matched a relevant client action during that session.

For a compatibility report, include `logs/latest.log`, your Minecraft version, Fabric Loader version, and the list of other installed mods. Please also mention which Kestrel setting you were testing.

## License

Kestrel is distributed under the MIT License. See [LICENSE](LICENSE) for details.
