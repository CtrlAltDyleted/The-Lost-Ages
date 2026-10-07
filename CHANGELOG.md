## 0.3.4

### Added
- Added the Circuit Slicer back into the game with a custom recipe and added support for its recipes that are removed by Create - Forge Frontier.
- Also added some recipes for the Applied Create circuits to the Circuit Slicer.
- Added the `Charged Redstone Block` from the 1.21.1 version of AppliedFlux.
- Added Sequenced Assembly recipes for Applied Flux's Circuit and Processor.

### Changed
- The mod now sets ExtendedAE's Assembler Matrix maximum size to 7x7.
- Fixed recipe for Accumulation Processor using Reaction Chamber.

## 0.3.3

### Added
- Added 27 more Reaction Chamber byproduct recipes, covering all metallurgy dirty-dust washing recipes.

## 0.3.2

### Added
- Added Reaction Chamber recipes for Redstone from Dirty Iron Dust and Glowstone Dust from Dirty Tin Dust.

### Fixed
- Restored the three standard AE2 Charger processing recipes removed by Forge Frontier.
- Changed the Logic Circuit printing ingredient from Gold Ingots to Create Gold Sheets.
- Restored the ExtendedAE Extended Inscriber to JEI.
- Enabled NBT matching for the Water, Cobblestone, and Lava Infinity Cell quests while preserving their quest and task IDs.

### Changed
- Increased the Sky Insulating Resin reaction batch to 16 of each solid ingredient and 64 output for parity with Mega Cells.

## 0.3.1

### Fixed
- Restored AE2 Inscriber and its recipes, fixing inaccessible Applied Flux processor production.

## 0.3.0

### Added
- Restored Pipez and added Pipez Lag Fix as required dependencies.
- Added Create Item Application recipes for Pipez item, fluid, and energy pipes.
- Added a Sequenced Assembly recipe for the Universal Pipe using Blaze Brass Casing and the item, fluid, and energy pipes.
- Added Pretty Pipes based Sequenced Assembly Recipes for the Pipez upgrades.
- Added Mob Grinding Utils recipes that use steel and Create components, including superheated mixing for Dreadful and Delightful Dirt.
- Added optional Create Style Pipez and Mob Grinding Utils: Vanillafied resource pack links to the dependency downloader.

### Changed
- Renamed the mod ID, Java package, resource namespaces, and JAR to `thelostages`.
- Preserved loading of blocks and items saved by earlier versions under the previous mod ID.
- Updated the Mob Grinding Utils quests.

## 0.2.1

### Added
- Added AppliedFlux Forge Energy to JEI for Export Card filter selection.
- Added Forge Energy export to compatible items in the player's inventory and supported Curios slots, including AE2 powered items.

### Fixed
- Fixed Export Card transfers when a wireless terminal is equipped in the Curios terminal slot.
- Fixed inventory slot selection in the Export Card screen so a single click changes the fluid/item exported without picking up the item in the inventory slot.

## 0.2.0

### Added
- Added AE2 Import Export Card Curios Export Card integration for equipped fluid/fueling tanks.
- Added the Infinity Lava Cell and matching quest to the AE2 quest chapter additions.
- Updated the mod icon to a new custom icon made by me

## 1.4

### Added
- Added Active Certusite and Active Skystonium Resource Vent duplication through Create sequenced assembly
- Added Certusite to Certus Quartz Crystal processing in the AdvancedAE Reaction Chamber
- Added non-placeable incomplete Active Certusite and Active Skystonium Vent items for sequenced assembly

### Changed
- Changed compatibility to Create: Forge Frontier 3.1.4
- Changed Skystonium processing to use Create: Dragons Plus black dye instead of Create: Enchantment Industry ink
- Changed Active Certusite Vent duplication to use an AE2 Quartz Block
- Changed Active Skystonium Vent duplication to use an AE2 Smooth Sky Stone Block
- Changed Resource Vents integration to use native mod resources instead of managed KubeJS scripts
- Changed AE2 creative compacting recipes to use native mod resources instead of managed KubeJS
- Changed Curios terminal integration to use native item tag resources instead of managed KubeJS
- Changed Applied Create Creative Stress Cell recipe suppression to use native datapack resources
- Changed AE2 facade hiding to use the AE2 client configuration instead of KubeJS

### Removed
- Removed the obsolete Lost Ages AE2 Charger recipe override now that Forge Frontier provides the Charger recipe
- Removed obsolete Lost Ages Resource Vents KubeJS scripts
- Removed obsolete Lost Ages AE2 KubeJS scripts

## 1.3

### Added
- Added AppliedFlux progression and Certusite/Skystonium vent quests
- Added dedicated-server filtering for known Radium and RenderType log spam
- Added Applied Create Creative Stress Cell recipe

### Changed
- Updated compatibility for Create: Forge Frontier 3.1.1
- Updated AE2 quest integration to preserve Forge Frontier's current AE2 chapter and progression
- Removed Lost Ages Spatial IO progression

### Fixed
- Removed the Stress Cell to Creative Motor shortcut recipe
- Prevented dedicated servers from patching the AE2 client config
