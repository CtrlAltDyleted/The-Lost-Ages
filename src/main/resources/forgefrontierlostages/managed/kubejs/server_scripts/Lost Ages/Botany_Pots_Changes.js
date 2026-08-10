// Botany_Pots_Changes.js
// Managed by The Lost Ages. Do not edit manually.

ServerEvents.recipes(function(event) {

    var COLORS = [
        'white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime',
        'pink', 'gray', 'light_gray', 'cyan', 'purple', 'blue',
        'brown', 'green', 'red', 'black'
    ];

    var MATERIALS = ['terracotta']
        .concat(COLORS.map(function(c) { return c + '_terracotta'; }))
        .concat(COLORS.map(function(c) { return c + '_glazed_terracotta'; }))
        .concat(COLORS.map(function(c) { return c + '_concrete'; }));

    event.shapeless('farmersdelight:rich_soil_farmland', [
        'farmersdelight:rich_soil',
        '#minecraft:hoes'
    ])
        .id('forgefrontierlostages:botany_pots/rich_soil_farmland_from_hoe')
        .damageIngredient('#minecraft:hoes', 1);

    MATERIALS.forEach(function(mat) {
        var materialItem = 'minecraft:' + mat;
        var regularPot   = 'botanypots:' + mat + '_botany_pot';
        var hopperPot    = 'botanypots:' + mat + '_hopper_botany_pot';

        event.remove({ output: regularPot });
        event.remove({ output: hopperPot });

        event.shaped(regularPot, [
            'XYX',
            'XZX',
            'XXX'
        ], {
            X: materialItem,
            Y: 'farmersdelight:rich_soil_farmland',
            Z: 'minecraft:flower_pot'
        }).id('forgefrontierlostages:botany_pots/' + mat + '_botany_pot');

        event.shaped(hopperPot, [
            'XYX',
            'XZX',
            'XAX'
        ], {
            X: materialItem,
            Y: 'farmersdelight:rich_soil_farmland',
            Z: 'minecraft:flower_pot',
            A: 'minecraft:hopper'
        }).id('forgefrontierlostages:botany_pots/' + mat + '_hopper_botany_pot');

        event.shapeless(hopperPot, [
            regularPot,
            'minecraft:hopper'
        ]).id('forgefrontierlostages:botany_pots/' + mat + '_hopper_upgrade');
    });

    MATERIALS.forEach(function(mat) {
        var elitePot       = 'botanypotstiers:elite_' + mat + '_botany_pot';
        var eliteHopperPot = 'botanypotstiers:elite_' + mat + '_hopper_botany_pot';

        event.shapeless(eliteHopperPot, [
            elitePot,
            'minecraft:hopper'
        ]).id('forgefrontierlostages:botany_pots_tiers/elite_' + mat + '_hopper_upgrade');
    });

    MATERIALS.forEach(function(mat) {
        var elitePot       = 'botanypotstiers:elite_' + mat + '_botany_pot';
        var eliteHopperPot = 'botanypotstiers:elite_' + mat + '_hopper_botany_pot';
        var ultraPot       = 'botanypotstiers:ultra_' + mat + '_botany_pot';
        var ultraHopperPot = 'botanypotstiers:ultra_' + mat + '_hopper_botany_pot';

        event.remove({ output: ultraPot });
        event.remove({ output: ultraHopperPot });

        event.shaped(ultraPot, [
            'RNR',
            'DPD'
        ], {
            R: 'create_dd:refined_radiance',
            N: 'minecraft:nether_star',
            D: 'minecraft:diamond_block',
            P: elitePot
        }).id('forgefrontierlostages:botany_pots_tiers/ultra_' + mat + '_botany_pot');

        event.shaped(ultraHopperPot, [
            'RNR',
            'DPD'
        ], {
            R: 'create_dd:refined_radiance',
            N: 'minecraft:nether_star',
            D: 'minecraft:diamond_block',
            P: eliteHopperPot
        }).id('forgefrontierlostages:botany_pots_tiers/ultra_' + mat + '_hopper_botany_pot');

        event.shapeless(ultraHopperPot, [
            ultraPot,
            'minecraft:hopper'
        ]).id('forgefrontierlostages:botany_pots_tiers/ultra_' + mat + '_hopper_upgrade');
    });

    MATERIALS.forEach(function(mat) {
        var ultraPot          = 'botanypotstiers:ultra_' + mat + '_botany_pot';
        var ultraHopperPot    = 'botanypotstiers:ultra_' + mat + '_hopper_botany_pot';
        var creativePot       = 'botanypotstiers:creative_' + mat + '_botany_pot';
        var creativeHopperPot = 'botanypotstiers:creative_' + mat + '_hopper_botany_pot';

        event.remove({ output: creativePot });
        event.remove({ output: creativeHopperPot });

        event.shaped(creativePot, [
            'SAS',
            'NPN'
        ], {
            S: 'create_dd:shadow_steel',
            A: 'minecraft:enchanted_golden_apple',
            N: 'minecraft:netherite_block',
            P: ultraPot
        }).id('forgefrontierlostages:botany_pots_tiers/creative_' + mat + '_botany_pot');

        event.shaped(creativeHopperPot, [
            'SAS',
            'NPN'
        ], {
            S: 'create_dd:shadow_steel',
            A: 'minecraft:enchanted_golden_apple',
            N: 'minecraft:netherite_block',
            P: ultraHopperPot
        }).id('forgefrontierlostages:botany_pots_tiers/creative_' + mat + '_hopper_botany_pot');

        event.shapeless(creativeHopperPot, [
            creativePot,
            'minecraft:hopper'
        ]).id('forgefrontierlostages:botany_pots_tiers/creative_' + mat + '_hopper_upgrade');
    });

});
