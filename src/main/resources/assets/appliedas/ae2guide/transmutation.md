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

Automates Astral Sorcery's **starlight item transmutation** recipes.

<Recipe id="appliedas:starlight_transmutation_chamber" />

## Usage

1. Connect any face to ME with power and one channel.
2. Supply the recipe's required constellation. Place the chamber in a focal point's center column with open sky at night, or use a **Linking Tool** to connect a working focus crystal or lens. [Starlight P2P tunnels](starlight_p2p.md) also work.
3. Insert ingredients by hand, with hoppers, ME Export Buses or Pattern Providers. Products enter ME storage or wait in the output slots if ME cannot accept them.

Processing pauses when starlight or network access is lost, or output space is insufficient. It resumes when conditions return.

## Processing time

| Supply | Default time |
| --- | --- |
| Focal point | 1200 ticks (60 seconds) |
| Focus crystal or lens | 200 ticks (10 seconds) |
| Overclock | 20 ticks (1 second) |

P2P transfer preserves these times. If both starlight sources meet the recipe's requirements, crystal or lens supply takes priority.

Enable **Overclock** to spend **5 Lm of Aion** at the start of each operation. If lumen is insufficient, the normal processing time applies.

## Auto pull

Drag an item from JEI or left-click a **Stock config** marker with a held item to set that input slot's ingredient. Middle-click to set its amount.

Enable **Auto pull** to replenish ingredients from ME and return excess or mismatched items.
