ServerEvents.recipes(event => {
    event.remove({ id: 'easy_villagers:auto_trader' });

    event.custom({
        type: 'create:compacting',
        ingredients: [
            { item: 'create:framed_glass_pane' },
            { item: 'create:framed_glass_pane' },
            { item: 'create:framed_glass_pane' },
            { item: 'create:framed_glass_pane' },
            { item: 'create:framed_glass_pane' },
            { item: 'minecraft:netherite_ingot' },
            { item: 'create:brass_ingot' },
            { item: 'create:brass_ingot' },
            { item: 'create_dd:chromatic_compound' }
        ],
        results: [
            { item: 'easy_villagers:auto_trader' }
        ]
    }).id('forgefrontierlostages:compacting/auto_trader');
});
