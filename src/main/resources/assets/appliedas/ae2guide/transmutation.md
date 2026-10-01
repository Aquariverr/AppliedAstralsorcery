---
navigation:
  title: Starlight Transmutation Chamber
  parent: appliedas:index.md
  position: 28
  icon: appliedas:starlight_transmutation_chamber
item_ids:
  - appliedas:starlight_transmutation_chamber
---
# Starlight Transmutation Chamber

The chamber automates Astral Sorcery's **starlight item transmutation** recipes, including recipes with several ingredients. Its quartz glass enclosure holds a suspended workpiece inside a gilded marble frame.

<Recipe id="appliedas:starlight_transmutation_chamber" />

Craft it on a **Resonating Crafting Table** at night: place a Molecular Assembler in the center, a Glass Lens above, a Gold Ingot below, an Aberrant Processor on either side and Runed Marble in the four corners. Supply four Quartz Glass blocks on the Focus Relays shown. Crafting takes **5 seconds**.

1. Connect any face to a powered ME network with an available channel.
2. Place the chamber **in the focal point's center column**, with an open sky above, to use that focal point's starlight directly at night without a crystal. Alternatively, use a **Linking Tool** to connect an attuned Collector Crystal at its matching focal point, directly or through lenses, with a clear beam path. Recipes that specify a constellation require starlight from that constellation. Ordinary crystals outside their matching focal point cannot supply starlight.
3. Place all required ingredients in the nine input slots. Hoppers, ME Export Buses and Pattern Providers can also supply items from any face. Repeated ingredients can share a stack.
4. The chamber follows the recipe's original duration and sends every completed product directly to ME. No Import Bus is required.

Enable **Overclock** to consume **25 Lm of Aion** from ME at the start of each operation and set its duration to **20 ticks (1 second)**. It is off by default. If less than 25 Lm is available, the operation uses its original recipe duration without consuming lumen. Starlight, power, ingredients and output space are still required. The mode is decided at the start of each operation; changes to the switch or replenished lumen apply to the next operation. A paid operation retains its mode and payment through interruptions and save/load. Cancelling a job by removing its ingredients does not refund lumen already consumed.

Enable **Auto pull** to stock inputs like an **ME Interface**. The nine configuration markers correspond to the nine input slots in order. Missing items are imported; excess items are returned to ME. Clearing a marker returns all contents of its slot. Changing the item returns the old contents before importing the new item. If ME cannot accept a return, the remaining items stay in the input buffer and are retried.

Configuration and actual stock are displayed separately, and actual inputs remain accessible in automatic mode. Left-click with a held stack to set a target or add its quantity to the same target. Right-click to subtract the held quantity, or one with an empty hand. Left-click with an empty hand to clear. Drag from JEI to mark an item. Middle-click an existing marker to enter an amount or use adjustment buttons; Enter/Done saves and Esc/Cancel discards changes. Zero clears the marker. Scroll to adjust by 1, or Shift-scroll by 10, up to the item's slot stack limit. Markers consume no items; transfers use AE2's powered transfer rules and do not request autocrafting. Turning auto pull off stops both imports and returns, retaining configuration and stock.

Click anywhere in the constellation panel to open JEI transmutation recipes. On hover, the recipe hint appears above the pointer and the constellation name below it.

If ME storage is full, products remain in the nine output slots and retry delivery. A full output buffer pauses processing before consuming any materials. Loss of ME power or starlight also pauses the operation; it resumes when the connection returns. Save/load preserves inputs, products and progress, and breaking the chamber drops its contents.

Direct focal transmutation pauses immediately during the day, when the sky above is obstructed, or when the focal point disappears. Progress resumes when conditions return. A separate valid incoming beam can still power the chamber.

The chamber handles item transmutation recipes; block transmutation and Liquid Starlight reactions use their original devices.
