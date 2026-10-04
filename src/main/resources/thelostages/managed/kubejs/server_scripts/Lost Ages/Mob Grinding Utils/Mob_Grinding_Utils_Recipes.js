ServerEvents.recipes(event => {
    const id = name => 'thelostages:mob_grinding_utils/' + name
    const steel = '#forge:ingots/steel'

    function replace(original, output, pattern, keys, name) {
        event.remove({ id: 'mob_grinding_utils:' + original })
        event.shaped(output, pattern, keys).id(id(name))
    }

    replace('recipe_spikes', 'mob_grinding_utils:spikes',
        [' S ', 'SBS', ' C '], {
            S: steel, B: 'create_sa:brass_sword', C: 'create:brass_casing'
        }, 'spikes')
    replace('recipe_saw', 'mob_grinding_utils:saw',
        ['SPS', 'PMP', 'SCS'], {
            S: steel, P: 'mob_grinding_utils:spikes',
            M: 'create:precision_mechanism', C: 'create_dd:chromatic_compound'
        }, 'saw')
    replace('recipe_fan', 'mob_grinding_utils:fan',
        [' P ', 'SFS', 'PCP'], {
            S: steel, P: 'create:propeller', F: 'create:encased_fan',
            C: 'create_dd:chromatic_compound'
        }, 'fan')
    replace('recipe_entity_conveyor', '6x mob_grinding_utils:entity_conveyor',
        ['BBB', 'SSS'], { S: steel, B: 'create:belt_connector' }, 'entity_conveyor')

    replace('recipe_absorbtion_hopper', 'mob_grinding_utils:absorption_hopper',
        [' E ', ' O ', 'OCO'], {
            E: 'minecraft:ender_eye', O: '#forge:obsidian',
            C: 'create:smart_chute'
        }, 'absorption_hopper')
    replace('recipe_tank_sink', 'mob_grinding_utils:tank_sink',
        [' I ', 'ECE', ' T '], {
            I: 'minecraft:iron_bars', E: 'minecraft:ender_eye',
            C: 'create:smart_chute', T: 'mob_grinding_utils:tank'
        }, 'tank_sink')

    replace('recipe_tank', 'mob_grinding_utils:tank',
        ['GSG', 'STS', 'GGG'], {
            S: steel, G: 'create:framed_glass', T: 'create:fluid_tank'
        }, 'tank')
    replace('recipe_jumbotank', 'mob_grinding_utils:jumbo_tank',
        ['SSS', 'TFT', 'BBB'], {
            S: steel, B: 'ad_astra:steel_block',
            T: 'mob_grinding_utils:tank', F: 'create:fluid_tank'
        }, 'jumbo_tank')
    replace('recipe_tintedglass', '4x mob_grinding_utils:tinted_glass',
        ['TST', 'G G', 'TST'], {
            S: steel, T: 'minecraft:tinted_glass', G: 'create:framed_glass'
        }, 'tinted_glass')
    replace('recipe_solidifier', 'mob_grinding_utils:xpsolidifier',
        ['EPE', 'SCS', ' T '], {
            E: 'mob_grinding_utils:entity_conveyor', P: 'create:mechanical_press',
            S: steel, C: 'create:smart_chute', T: 'mob_grinding_utils:tank'
        }, 'xp_solidifier')

    const quartz = 'create:polished_rose_quartz'
    const propeller = 'create:propeller'
    const brassSheet = 'create:brass_sheet'
    replace('recipe_fan_upgrade_height', 'mob_grinding_utils:fan_upgrade_height',
        [' P ', 'QPQ', ' B '], { Q: quartz, P: propeller, B: brassSheet }, 'fan_upgrade_height')
    replace('recipe_fan_upgrade_width', 'mob_grinding_utils:fan_upgrade_width',
        ['Q Q', 'PBP', 'Q Q'], { Q: quartz, P: propeller, B: brassSheet }, 'fan_upgrade_width')
    replace('recipe_fan_upgrade_speed', 'mob_grinding_utils:fan_upgrade_speed',
        ['QPQ', 'PBP', 'QPQ'], { Q: quartz, P: propeller, B: brassSheet }, 'fan_upgrade_speed')

    const sawUpgrades = [
        ['arthropod', 'minecraft:fermented_spider_eye'],
        ['smite', 'minecraft:rotten_flesh'],
        ['fire', 'minecraft:fire_charge'],
        ['beheading', '#forge:heads'],
        ['looting', 'minecraft:lapis_lazuli'],
        ['sharpness', 'create_sa:experience_sword']
    ]
    sawUpgrades.forEach(([name, ingredient]) => {
        replace('recipe_saw_upgrade_' + name, 'mob_grinding_utils:saw_upgrade_' + name,
            [' I ', 'BQB', ' I '], {
                B: 'create:brass_nugget', I: ingredient, Q: quartz
            }, 'saw_upgrade_' + name)
    })
    replace('recipe_absorbtion_upgrade', 'mob_grinding_utils:absorption_upgrade',
        ['OEO', 'EQE', ' C '], {
            E: 'minecraft:ender_pearl', Q: quartz,
            O: 'minecraft:obsidian', C: 'create:smart_chute'
        }, 'absorption_upgrade')

    function dirtMixing(name, themedItems) {
        const ingredients = [{ item: 'minecraft:dirt' }]
        themedItems.forEach(item => {
            for (let count = 0; count < 8; count++) ingredients.push({ item: item })
        })
        for (let count = 0; count < 16; count++) {
            ingredients.push({ item: 'create_enchantment_industry:super_experience_nugget' })
        }
        event.custom({
            type: 'create:mixing',
            ingredients: ingredients,
            results: [{ item: 'mob_grinding_utils:' + name + '_dirt', count: 1 }],
            heatRequirement: 'superheated'
        }).id(id(name + '_dirt_mixing'))
    }
    dirtMixing('dreadful', ['minecraft:rotten_flesh', 'minecraft:bone', 'minecraft:gunpowder'])
    dirtMixing('delightful', ['minecraft:egg', 'minecraft:feather', 'minecraft:leather'])
})

ServerEvents.recipes(event => {
    const removedRecipes = [
        'gm_chicken_feed', 'recipe_cursed_feed', 'recipe_nutritious_feed',
        'recipe_mob_swab', 'recipe_entity_spawner',
        'recipe_spawner_upgrade_height', 'recipe_spawner_upgrade_width'
    ]
    removedRecipes.forEach(recipe => event.remove({ id: 'mob_grinding_utils:' + recipe }))

    const hiddenItems = [
        'null_sword', 'gm_chicken_feed', 'gm_chicken_feed_cursed',
        'nutritious_chicken_feed', 'mob_swab', 'mob_swab_used',
        'dark_oak_stone', 'entity_spawner', 'spawner_upgrade_height',
        'spawner_upgrade_width', 'ender_inhibitor_off', 'monocle',
        'rotten_egg', 'golden_egg'
    ]
    hiddenItems.forEach(item => event.remove({ output: 'mob_grinding_utils:' + item }))
})
