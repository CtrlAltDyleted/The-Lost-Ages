// AE2_Changes.js
// Managed by The Lost Ages. Do not edit manually.

ServerEvents.recipes(function(event) {

    function addSuperheatedCompactingRecipe(output, recipeId, cellInput) {
        event.custom({
            type: 'create:compacting',
            ingredients: [
                {
                    fluid: 'forge_frontier:creative_energy_fluid',
                    amount: 1000
                },
                {
                    item: 'create_additions_synthetics:creative_core'
                },
                {
                    item: cellInput
                }
            ],
            results: [
                {
                    item: output,
                    count: 1
                }
            ],
            heatRequirement: 'superheated'
        }).id(recipeId);
    }

    addSuperheatedCompactingRecipe(
        'ae2:creative_energy_cell',
        'forgefrontierlostages:compacting/creative_energy_cell',
        'megacells:mega_energy_cell'
    );

    addSuperheatedCompactingRecipe(
        'ae2:creative_item_cell',
        'forgefrontierlostages:compacting/creative_item_cell',
        'megacells:item_storage_cell_256m'
    );

    addSuperheatedCompactingRecipe(
        'ae2:creative_fluid_cell',
        'forgefrontierlostages:compacting/creative_fluid_cell',
        'megacells:fluid_storage_cell_256m'
    );

    addSuperheatedCompactingRecipe(
        'appflux:fe_creative_cell',
        'forgefrontierlostages:compacting/creative_fe_cell',
        'appflux:fe_256m_cell'
    );

    event.remove({ id: 'appliedcreate:creative_motor_from_stress_cell' });
    event.remove({ id: 'appliedcreate:creative_stress_cell' });

    addSuperheatedCompactingRecipe(
        'appliedcreate:creative_stress_cell',
        'forgefrontierlostages:compacting/creative_stress_cell',
        'appliedcreate:stress_storage_cell_256m'
    );

});

ServerEvents.tags('item', function(event) {

    event.add('curios:terminals', [
        'ae2:wireless_terminal',
        'advanced_ae:wireless_quantum_crafter_terminal',
        'expatternprovider:wireless_ex_ct'
    ]);

    const terminalOnlyItems = [
        'ae2:wireless_terminal',
        'advanced_ae:wireless_quantum_crafter_terminal',
        'expatternprovider:wireless_ex_ct'
    ];

    terminalOnlyItems.forEach(function(item) {
        event.remove('curios:curio', item);
    });

});