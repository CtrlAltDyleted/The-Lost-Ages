// Managed by The Lost Ages.
JEIEvents.hideItems(event => {
    const hiddenItems = [
        'mob_grinding_utils:null_sword',
        'mob_grinding_utils:gm_chicken_feed',
        'mob_grinding_utils:gm_chicken_feed_cursed',
        'mob_grinding_utils:nutritious_chicken_feed',
        'mob_grinding_utils:mob_swab',
        'mob_grinding_utils:mob_swab_used',
        'mob_grinding_utils:dark_oak_stone',
        'mob_grinding_utils:entity_spawner',
        'mob_grinding_utils:spawner_upgrade_height',
        'mob_grinding_utils:spawner_upgrade_width',
        'mob_grinding_utils:ender_inhibitor_off',
        'mob_grinding_utils:monocle',
        'mob_grinding_utils:rotten_egg',
        'mob_grinding_utils:golden_egg'
    ]
    hiddenItems.forEach(item => event.hide(item))
})
