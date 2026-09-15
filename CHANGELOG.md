## 1.4

### Added
- Added Active Certusite and Active Skystonium Resource Vent duplication through Create sequenced assembly
- Added Certusite to Certus Quartz Crystal processing in the AdvancedAE Reaction Chamber
- Added non-placeable incomplete Active Certusite and Active Skystonium Vent items for sequenced assembly

### Changed
- Updated compatibility for Create: Forge Frontier 3.1.4
- Changed Skystonium processing to use Create: Dragons Plus black dye instead of Create: Enchantment Industry ink
- Changed Active Certusite Vent duplication to use an AE2 Quartz Block
- Changed Active Skystonium Vent duplication to use an AE2 Smooth Sky Stone Block
- Moved Resource Vents integration from managed KubeJS scripts to native mod resources
- Moved AE2 creative compacting recipes and Curios terminal integration from managed KubeJS to native mod resources
- Moved Applied Create Creative Stress Cell recipe suppression to native datapack resources
- Moved AE2 facade hiding from KubeJS to the AE2 client config patcher

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
