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

The chamber automates Astral Sorcery's **starlight item transmutation** recipes.

<Recipe id="appliedas:starlight_transmutation_chamber" />

## Usage

Connect any face to a powered ME network with one available channel. Supply starlight using either method below. Recipes that specify a constellation require starlight from that constellation:

- Place the chamber **in a focal point's center column**, with an unobstructed sky above, to use its starlight directly at night. No crystal is needed.
- Use a **Linking Tool** to connect a **Starlight Focus Crystal**, directly or through lenses. The crystal must be attuned to the focal point's constellation, stand in its center column and have an open sky above. It supplies starlight at night while that constellation is visible; keep the beam path clear.

Place ingredients in the input slots, or supply them from any face with hoppers, ME Export Buses or Pattern Providers. Products go directly to ME, or stay in the output slots if ME storage is full.

By default, direct focal-point starlight takes **1,200 ticks (60 seconds)** per operation. Crystals and other transmitted starlight take **200 ticks (10 seconds)**. Matching transmitted starlight takes priority when both sources are available. In a local singleplayer world, use **Mods → Applied Astralsorcery → Config → Server Settings → Machine Processing Times** to adjust them. You can also edit `processing.transmutationFocalTicks` and `processing.transmutationStarlightTicks` in `config/appliedas-server.toml`; an existing `serverconfig/appliedas-server.toml` in the world save takes priority.

Transmutation pauses when the output slots cannot hold the products, ME goes offline or starlight is interrupted. It resumes when conditions return.

## Auto pull

Drag an item from JEI or left-click a **Stock config** marker with a held stack to set the ingredient. Middle-click to set its amount. The nine markers correspond to the nine input slots.

Enable **Auto pull** to replenish ingredients from ME according to the configuration and return excess or mismatched items to ME.

## Overclock

Enable **Overclock** to consume **5 Lm of Aion** from ME at the start of each operation. Its duration defaults to **20 ticks (1 second)**, configurable with `processing.transmutationOverclockTicks` in the same file. If lumen is insufficient, the current starlight source's processing time applies.
