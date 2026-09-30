ServerEvents.recipes(event => {
    const namespace = 'thelostages:'
    const pipeRecipes = ['item_pipe', 'fluid_pipe', 'energy_pipe', 'gas_pipe', 'universal_pipe']
    const upgradeRecipes = ['basic_upgrade', 'improved_upgrade', 'advanced_upgrade', 'ultimate_upgrade']

    pipeRecipes.concat(upgradeRecipes).forEach(name => event.remove({ id: 'pipez:' + name }))

    function application(name, base, applied) {
        event.custom({
            type: 'create:item_application',
            ingredients: [{ item: base }, { item: applied }],
            results: [{ item: 'pipez:' + name, count: 1 }]
        }).id(namespace + 'item_application/' + name)
    }

    application('item_pipe', 'prettypipes:pipe', 'create_dd:overburden_casing')
    application('fluid_pipe', 'ppfluids:fluid_pipe', 'create_dd:hydraulic_casing')
    application('energy_pipe', 'create_new_age:electrical_connector', 'create_dd:overcharged_casing')

    function assembly(name, startingItem, appliedItems) {
        const transitional = namespace + 'incomplete_' + name
        const sequence = appliedItems.map(item => ({
            type: 'create:deploying',
            ingredients: [{ item: transitional }, { item: item }],
            results: [{ item: transitional }]
        }))
        sequence.push({
            type: 'create:pressing',
            ingredients: [{ item: transitional }],
            results: [{ item: transitional }]
        })
        event.custom({
            type: 'create:sequenced_assembly',
            ingredient: { item: startingItem },
            transitionalItem: { item: transitional },
            sequence: sequence,
            results: [{ item: 'pipez:' + name, count: 1 }],
            loops: 1
        }).id(namespace + 'sequenced_assembly/' + name)
    }

    assembly('universal_pipe', 'create_dd:blaze_gold_casing', [
        'pipez:item_pipe', 'pipez:fluid_pipe', 'pipez:energy_pipe'
    ])
    assembly('basic_upgrade', 'prettypipes:blank_module', [
        'prettypipes:low_extraction_module', 'prettypipes:redstone_module'
    ])
    assembly('improved_upgrade', 'pipez:basic_upgrade', [
        'prettypipes:medium_extraction_module', 'prettypipes:round_robin_sorting_modifier'
    ])
    assembly('advanced_upgrade', 'pipez:improved_upgrade', [
        'prettypipes:high_extraction_module', 'prettypipes:medium_filter_module'
    ])
    assembly('ultimate_upgrade', 'pipez:advanced_upgrade', [
        'prettypipes:high_filter_module', 'prettypipes:high_speed_module',
        'createqol:shadow_radiance_casing'
    ])
})

ServerEvents.recipes(event => {
    event.remove({ id: 'pipez:copy_upgrade_infinity' })
    const hiddenItems = ['gas_pipe', 'infinity_upgrade']
    hiddenItems.forEach(name => event.remove({ output: 'pipez:' + name }))
})
