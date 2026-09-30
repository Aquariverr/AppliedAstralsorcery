---
navigation:
  title: ME Lumen Filament
  parent: appliedas:index.md
  position: 30
  icon: appliedas:me_lumen_filament
item_ids:
  - appliedas:me_lumen_filament
---
# ME Lumen Filament

An ME Lumen Filament requests a selected lumen type from nearby Lumen Arrays and stores it in the ME network. Ordinary Lumen Filaments can relay lumen over longer distances.

## Crafting

Use a **Resonating Crafting Table** with the Lumen Filament layout: a Lumen Filament in the center, a Lumen Processor above it, and a Fluix Block replacing the bottom Infused Wood. Start at night with a Resonating Wand. Crafting takes **5 seconds** and produces **1 ME Lumen Filament**.

<Recipe id="appliedas:me_lumen_filament" />

## Connections and setup

1. Connect the filament to a powered ME network and place a Lumen Storage Cell with free space in an ME Drive or ME Chest.
2. **Right-click** to open the configuration screen, then choose the lumen to collect from the list of icons and names. Use the page buttons or mouse wheel to browse other types. No lumen crystal is needed.
3. Make sure a nearby Lumen Array contains the selected lumen. Each connection along the path from the array to the ME Lumen Filament must be no longer than **16 blocks**, with no blocks obstructing transmission. Place ordinary Lumen Filaments along the route if needed.

At normal game speed, a filament collects up to **500 Lm per second**. Each ME Lumen Filament requires **1 ME channel**.

The small ME base automatically faces a connected cable or ME device on any of its six sides. With multiple connections, it keeps its current orientation; removing that connection makes the base turn toward another connected side.

The filament continuously emits Astral Sorcery's native star particles and provides the same light level of 6 as the ordinary filament. When it successfully receives or relays lumen, the stars take on that lumen's color and lumen glyphs occasionally appear. The colored afterglow lasts about 3 seconds after transmission stops.

## Controls

- **Right-click**: open the configuration screen to view the requested type, status, and internal buffer.
- **Click a lumen type in the list**: select the type to request. The selected type is highlighted.
- **Click "Stop requests"**: pause new requests. Buffered lumen will still be sent to the ME network.

Shift + right-click does not cycle types, and holding a lumen crystal does not directly set the requested type. AE2 wrenches and Memory Cards retain their normal functions.

The filament waits if power is lost, the network cannot accept lumen, or the source array has too little lumen. Lumen already received stays in the filament until it can be stored in the network. Changing the requested type or stopping requests does not discard buffered lumen.

Check the status in the configuration screen.
