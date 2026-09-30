---
navigation:
  title: Automatic Starmetal Chisel
  parent: index.md
  position: 35
  icon: appliedas:auto_starmetal_chisel
item_ids:
  - appliedas:auto_starmetal_chisel
---
# Automatic Starmetal Chisel

## Crafting

Use a **Resonating Crafting Table**. Arrange Runed Marble, a Starmetal Chisel, Starmetal Ingots, a Lumen Processor and Infused Wood in the central grid as shown below.

The Focus Relays below the grid follow the bottom of the native Lumen Filament recipe, requiring an additional **4 Gold Nuggets, 3 Enriched Infused Wood and 1 Vibrant Infused Wood**. Start at **night** with a Resonating Wand. Crafting takes **5 seconds** and produces **1 Automatic Starmetal Chisel**.

<Recipe id="appliedas:auto_starmetal_chisel" />

## Usage

Insert rock, celestial or aberrant crystals, starmetal ingots, or artifacts into the left input slot. Each operation takes **2 seconds** and **25 Lm of Evorsio**. No separate tool or repairs are needed.

The machine stores **2,000 Lm**. Supply Evorsio through an ME export bus on any face, or a nearby lumen relay network. Lumen relays still need to meet their range and line-of-sight requirements.

## Products

- **Crystals** split using Astral Sorcery's rules. Both crystals enter the output slots. Crystals with fewer than two attribute tiers cannot split.
- **Starmetal ingots** produce one stardust. Without enchantments, each operation has a **90%** chance to consume the ingot.
- **[Artifacts](artifact.md)** produce their matching shard. Without enchantments, each operation has a **40%** chance to consume the artifact.

Any retained material also enters the output slots. Leave **two empty output slots** before processing. Work pauses when lumen or output space runs out.

## Input and output settings

Click the wrench tab to open **Settings**. Click it again to return to the item slots.

The six buttons represent up, down, north, south, east and west. **Left-click** cycles through input, output and input/output; **right-click** cycles backward. Blue means input, orange means output and purple allows both. The bottom defaults to output; all other faces default to input.

In **Mode: Item Slots**, **Auto input** pulls from adjacent containers and **Auto output** sends products to them. When these switches are off, hoppers, pipes and ME buses can still transfer items through the configured faces.

## Dropped items

Select **Mode: Dropped Items** to process drops in the block directly beside each input face. Each operation processes one item with the same time and lumen cost.

With **Take turns** enabled, the machine switches to another dropped item after each operation. When disabled, it keeps processing the same stack. Picking up, moving or changing the target resets unfinished progress without charging lumen.

Products and retained materials drop into the block directly beside an output face. The machine checks **Down, Up, North, South, West, East** in that order and pauses if no output position is available. Input/output faces serve both purposes.

This mode does not use the internal slots and pauses automatic transfers and pipe access. You can still remove stored items by hand.

## Enchantments

Use an **enchanting table** or **anvil** to enchant the machine. **Fortune** reduces crystal attribute loss and material consumption. Fortune III lowers the chance to consume ingots and artifacts to **69%** and **31%**, respectively. Since the machine has no durability cost, **Unbreaking has no extra effect**.

Enchantments do not change processing time or lumen cost. Breaking the machine keeps its enchantments and drops stored items, but loses stored lumen and progress.

[Back to Applied Astralsorcery](index.md)
