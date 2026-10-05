---
navigation:
  title: Altar Automation
  icon: appliedas:altar_automation_interface
  position: 80
  parent: appliedas:index.md
item_ids:
  - appliedas:altar_automation_interface
---
# Altar Automation Interface

## Crafting

Craft in an **Iridescent Crafting Altar** using a [Constellation Core](constellation_core.md) and the materials shown below. Start with a Resonating Wand and drop the [stable artifact](artifact.md) near the altar.

<Recipe id="appliedas:altar_automation_interface" />

## Usage

1. Build the altar structure and relay layout, then empty the altar and all relays in that layout. Place the interface against an **ME Pattern Provider**, within **16 blocks** of the altar.
2. Encode an AE2 **processing pattern** with one craft's inputs and outputs. Include the altar grid, relay and dropped-item ingredients, plus any additional fluids and lumen. Transferring an altar recipe from JEI into a Pattern Encoding Terminal includes these resources automatically. Grid and relay fluid ingredients still use filled containers.
3. Put the pattern in the provider and request crafting from an ME Terminal. The interface runs one craft at a time, places the ingredients, starts the altar, and sends products and container returns back to the original provider. Items that cannot be returned stay in the interface and can be extracted with an ME Import Bus.

## Notes

- The interface uses only the nearest loaded altar in range, even if it is busy or unsuitable. Right-click the interface with an empty hand to check its target and status.
- The interface has **16 fluid tanks of 64,000 mB each** and stores **64,000 Lm per lumen type**. Processing patterns, fluid pipes and ME buses can fill its buffers. Buffered resources are saved with the interface and drawn by its active crafting job; remaining requirements can still come from nearby chalices and the lumen network. Existing item-only patterns can use separately supplied resources.
- Altar tier, night, focus and starlight requirements still apply. [ME Chalices](chalice.md) and [ME Lumen Arrays](lumen_array.md) can also supply fluids and lumen.
- Recipes that replace the altar itself are not supported.
- Do not manually change ingredients in the altar or relays while crafting. Breaking the interface drops its stored items and discards buffered fluids and lumen; ingredients already placed remain where they are.
