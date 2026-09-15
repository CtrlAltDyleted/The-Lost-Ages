ServerEvents.recipes(event => {
  const ventRecipes = [
    {
      vent: 'create_resource_vents:active_certusite_vent',
      incomplete: 'forgefrontierlostages:incomplete_active_certusite_vent',
      material: 'forgefrontierlostages:certusite',
      fluid: 'create_ethium:echo_compound_fluid',
      id: 'forgefrontierlostages:sequenced_assembly/vent_duplication/active_certusite_vent'
    },
    {
      vent: 'create_resource_vents:active_skystonium_vent',
      incomplete: 'forgefrontierlostages:incomplete_active_skystonium_vent',
      material: 'forgefrontierlostages:skystonium',
      fluid: 'create_dragons_plus:black_dye',
      id: 'forgefrontierlostages:sequenced_assembly/vent_duplication/active_skystonium_vent'
    }
  ]

  ventRecipes.forEach(recipe => {
    event.custom({
      type: 'create:sequenced_assembly',
      ingredient: { item: recipe.vent },
      transitionalItem: { item: recipe.incomplete },
      loops: 3,
      results: [
        { item: recipe.vent, count: 2 }
      ],
      sequence: [
        {
          type: 'create:deploying',
          ingredients: [
            { item: recipe.incomplete },
            { item: recipe.material }
          ],
          results: [
            { item: recipe.incomplete }
          ]
        },
        {
          type: 'create:deploying',
          ingredients: [
            { item: recipe.incomplete },
            { item: 'forge_frontier:compressed_mechanism_block_1x' }
          ],
          results: [
            { item: recipe.incomplete }
          ]
        },
        {
          type: 'create:deploying',
          ingredients: [
            { item: recipe.incomplete },
            { item: 'createqol:shadow_radiance_block' }
          ],
          results: [
            { item: recipe.incomplete }
          ]
        },
        {
          type: 'create:filling',
          ingredients: [
            { item: recipe.incomplete },
            { fluid: recipe.fluid, amount: 1000 }
          ],
          results: [
            { item: recipe.incomplete }
          ]
        },
        {
          type: 'create:pressing',
          ingredients: [
            { item: recipe.incomplete }
          ],
          results: [
            { item: recipe.incomplete }
          ]
        }
      ]
    }).id(recipe.id)
  })
})
