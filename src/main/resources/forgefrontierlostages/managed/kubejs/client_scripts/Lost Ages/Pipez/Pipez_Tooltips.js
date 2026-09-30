ItemEvents.tooltip(event => {
    const note = 'Upgrading resets any existing configuration.'
    event.add('pipez:basic_upgrade', note)
    event.add('pipez:improved_upgrade', note)
    event.add('pipez:advanced_upgrade', note)
})

JEIEvents.hideItems(event => {
    event.hide('pipez:gas_pipe')
    event.hide('pipez:infinity_upgrade')
})
