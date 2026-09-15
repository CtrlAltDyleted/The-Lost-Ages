StartupEvents.registry('block', event => {
    event.create('forgefrontierlostages:incomplete_active_certusite_vent')
        .displayName('Incomplete Active Certusite Vent')
        .soundType('stone')
        .mapColor('stone')
        .hardness(2.0)
        .requiresTool(true)
        .renderType('solid')
        .fullBlock(true)
        .tagBlock('minecraft:mineable/pickaxe');

    event.create('forgefrontierlostages:incomplete_active_skystonium_vent')
        .displayName('Incomplete Active Skystonium Vent')
        .soundType('stone')
        .mapColor('stone')
        .hardness(2.0)
        .requiresTool(true)
        .renderType('solid')
        .fullBlock(true)
        .tagBlock('minecraft:mineable/pickaxe');
});
