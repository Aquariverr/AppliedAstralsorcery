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

<Recipe id="appliedas:auto_starmetal_chisel" />

## Usage

Supply **Evorsio lumen** through an ME Export Bus or nearby Lumen Filament. The machine holds **2,000 Lm**. Each operation defaults to **25 Lm** and **2 seconds**.

| Input | Result and requirements |
| --- | --- |
| Rock, Celestial or Aberrant Crystal | Splits into two crystals; requires at least 2 total attribute tiers |
| Starmetal Ingot | 1 Stardust; 90% chance to consume the ingot without enchantments |
| Artifact | Matching shard; 40% chance to consume the artifact without enchantments |

Put materials in the left input slot and leave **two empty output slots**. Retained materials also enter the outputs. Processing pauses when lumen or output space runs out.

## Input and output

Click the wrench to open **Settings**. Left-click a face to cycle through input, output, input/output and none; right-click cycles backward. The bottom defaults to output and other faces to input.

- **Item Slots:** Auto input and Auto output transfer items to and from adjacent containers. Hoppers, pipes and ME buses still follow face settings when these switches are off.
- **Dropped Items:** Processes drops in the block beside an input face and drops products beside an output face. Internal slots and pipe transfers are disabled. Processing pauses if no output position is available.
- **Take turns:** Switches drops after each operation. Turn it off to process the same stack continuously.

## Enchantments

Use an enchanting table or anvil. **Fortune** reduces crystal attribute loss and material consumption. Fortune III lowers ingot and artifact consumption chances to **69%** and **31%**.

Breaking the machine retains enchantments and drops stored items, but loses lumen and progress.
