
# The Lost Ages

<img src="https://github.com/user-attachments/assets/e0ca3a64-9b79-4300-bb77-75000356b40b" alt="The Lost Ages" width="320">

**The Lost Ages** is an unofficial companion mod for **Create: Forge Frontier**, adding original quests, custom recipes, progression changes, mod integrations, and compatibility improvements.

It is designed specifically for Forge Frontier and is not intended to run as a standalone mod.

## Project Information

| | |
|---|---|
| Current Version | 1.0.0 |
| Release Status | Pending CurseForge review |
| Latest Published Version | 0.3.4 |
| Minecraft | 1.20.1 |
| Forge | 47.4.20+ |
| Required Modpack | Create: Forge Frontier 3.1.5 |
| Author | CtrlAltDyleted |

**[Download on CurseForge](https://www.curseforge.com/minecraft/mc-mods/the-lost-ages)**

## Installation

Starting with version 1.0.0, The Lost Ages includes a built-in **Dependency Installer**. The separate Dependency Download Wizard is no longer required.

### Client Installation

1. Install **Create: Forge Frontier 3.1.5** through your preferred Minecraft launcher.
2. Download `thelostages-1.0.0.jar` and place it in the instance's `mods/` folder.
3. Launch Minecraft. The Dependency Installer will open if any required add-ons are missing.
4. Click **Open Required Mods on CurseForge** to open the official download pages. You can also include the two optional resource packs.
5. Download the files through your browser. The installer monitors your Downloads folder and verifies each file automatically.
6. Once all required files are verified, click **Install All Required Mods** or **Install All Required Mods and Optional Resource Packs**.
7. After installation finishes, click **Close Minecraft** and launch the game again.

The installer copies the verified files into the correct directories. Original downloads remain untouched.

### Download Folder Detection

The installer automatically checks the standard Downloads folder when available.

If your browser saves files elsewhere, click **Choose Download Folder** and select the appropriate directory.

The selected folder is remembered for future use. Only that directory is monitored, and files are verified before installation.

### Required Add-ons

The Lost Ages requires nine additional mods beyond those already included in Forge Frontier:

- Applied Flux
- AE2 Network Analyser
- AE2: Crafting Tree
- Mob Grinding Utils
- Botany Pots
- Botany Trees
- Botany Pots Tiers
- Pipez
- Pipez Lag Fix

The Dependency Installer provides links to the exact required versions on CurseForge.

**No external downloader or CurseForge API key is required.** The Lost Ages does not bundle these third-party mod files or download them directly from CurseForge's API or CDN.

### Optional Resource Packs

The following resource packs were included in earlier versions of Create: Forge Frontier:

- **Lopy's Create Style Pipez**
- **Mob Grinding Utils: Vanillafied**

Both packs are optional. The installer can install them individually or alongside the required mods.

Installed resource packs are placed in `resourcepacks/` and enabled through Forge Frontier's existing OpenLoader configuration when Minecraft restarts.

### Dedicated Servers

Dedicated servers do not display the Dependency Installer or open browser download pages.

Install the nine required add-ons manually using the versions listed in the [dependency catalog](src/bootstrap/resources/dependencies.json).

When dependencies are missing, The Lost Ages writes information to:

`.thelostages/missing-dependencies.txt`

Install the required files into the server's `mods/` folder, then restart the server.

The same required add-ons must be installed on connecting clients.

## Features

### Quests and Progression

- Original quests and quest integrations designed for Forge Frontier
- Custom recipes and progression changes
- Adjustments to existing crafting requirements
- Compatibility improvements between installed mods

### Applied Energistics 2

- Additional AE2 and ExtendedAE integrations
- Applied Flux compatibility
- Curios terminal-slot integration
- Integration with AE2-related add-ons and crafting systems

### Automation and Resources

- Custom Certusite and Skystonium Resource Vents
- Mob Grinding Utils progression and integration
- Botany Pots, Botany Trees, and Botany Pots Tiers support
- Pipez integration with Pretty Pipes and Create
- Pipez Lag Fix compatibility
- Easy Villagers Auto Trader integration

The Lost Ages also includes configuration adjustments and compatibility fixes intended to make these systems work together within Forge Frontier.

## Compatibility and Managed Files

The Lost Ages assumes an existing **Create: Forge Frontier 3.1.5** installation.

It does not install, update, or enforce the base modpack's dependencies. Its Dependency Installer manages only the additional Lost Ages requirements.

Some gameplay integrations use files and configurations supplied by Forge Frontier.

**The Lost Ages manages certain modpack files automatically.** Files under its management may be restored or modified during startup, so manual changes to those files may be overwritten.

### Dependency Installation Safety

The Dependency Installer verifies downloaded files using pinned SHA-256 hashes, archive checks, and mod metadata.

When replacing an incompatible mod, the installer requires confirmation and preserves backups in:

`.thelostages/backups/`

The installer copies files rather than moving or deleting the original browser downloads.

If Forge rejects duplicate or incompatible mods before the installer opens, manual correction may be necessary.

## Development

The Lost Ages is developed for Minecraft 1.20.1 using Forge 47.4.20 and Java 17.

### Building

Use the included Gradle wrapper:

```powershell
.\gradlew.bat build
```

Compile-only reference JARs are expected in `Lost Ages Mods/mods/`, as configured in `build.gradle`.

The resulting release JAR is located at:

`build/libs/thelostages-1.0.0.jar`

The release contains an internal gameplay JAR that is extracted automatically at runtime. Do not install or distribute that internal artifact separately.

### Dependency Installer

The native installer consists of three main components:

- `LostAgesDependencyLocator.java`: Early Forge discovery, dependency detection, and catalog handling
- `Installation.java`: Download-folder monitoring, file verification, installation, backups, and rollback
- `ManagerWindow.java`: Swing interface, CurseForge page actions, and installation controls

The catalog at `src/bootstrap/resources/dependencies.json` defines the nine required add-ons and two optional resource packs.

The installer uses Java 17's filesystem monitoring APIs to detect downloaded files. It does not require external services, background applications, or developer API credentials.

The interface uses the Exo font under the SIL Open Font License 1.1. The font license and attribution are included in the repository.

For implementation details, see [CurseForge Workflow and Compliance](CURSEFORGE_COMPLIANCE.md).

## Quest Authorship

The Lost Ages contains original quests and related content created by **CtrlAltDyleted**.

Some quests authored by CtrlAltDyleted have also been incorporated into Create: Forge Frontier with permission and attribution.

Ownership of original content remains with its respective copyright holder.

## Unofficial Project Disclaimer

The Lost Ages is an independent, unofficial companion mod.

It is not affiliated with, endorsed by, or maintained by the developers of Create: Forge Frontier.

Create: Forge Frontier and all third-party mod names, assets, and trademarks belong to their respective owners.

## License

**Copyright © 2026 CtrlAltDyleted. All Rights Reserved.**

Original code, scripts, recipes, quests, integrations, documentation, configurations, and other original project content are protected by copyright.

Public access to the source code does not grant permission to copy, modify, redistribute, relicense, or create derivative works without authorization from the copyright holder.

Third-party material remains subject to its respective licenses and attribution requirements.

See:

- [LICENSE.txt](LICENSE.txt)
- [COPYRIGHT.md](COPYRIGHT.md)
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
