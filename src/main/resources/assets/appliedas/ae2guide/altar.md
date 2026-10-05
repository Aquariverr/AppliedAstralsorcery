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

<Recipe id="appliedas:altar_automation_interface" />

To craft it, start the Iridescent Crafting Altar with a Resonating Wand and drop a [stable artifact](artifact.md) nearby.

## Usage

1. Build the altar and Focus Relay layout, then empty their item slots. Place the interface against an **ME Pattern Provider**, within **16 blocks** of the altar.
2. Encode a **processing pattern** for one craft, including grid, relay and dropped ingredients, plus additional fluids and lumen. JEI recipe transfer fills these in; grid and relay fluid ingredients still use filled containers.
3. Put the pattern in the provider and request crafting in an ME Terminal. The interface places ingredients, starts the altar and returns products and leftover items to the provider. An ME Import Bus can extract items that remain in the interface.

Only the nearest loaded altar is selected. **Right-click with an empty hand** to check its target and status. Altar tier, night, focus and starlight requirements still apply. [Chalices](chalice.md) and [arrays](lumen_array.md) can supply fluids and lumen separately.

Recipes that replace the altar itself are unsupported. Do not change altar or relay ingredients during a craft. Breaking the interface loses its stored fluids and lumen.
