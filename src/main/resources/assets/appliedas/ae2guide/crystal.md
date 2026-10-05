---
navigation:
  title: Aberrant Crystal
  parent: appliedas:index.md
  position: 5
  icon: appliedas:astral_fluix_crystal
item_ids:
  - appliedas:astral_fluix_crystal
  - appliedas:astral_fluix_cluster
  - appliedas:astral_fluix_block
---
# Aberrant Crystal

## Obtaining crystals

Prepare Liquid Starlight over solid ground and drop the recipe ingredients into it together. They consume one fluid block to form a cluster.

<Recipe id="appliedas:liquid_starlight/form_astral_fluix_cluster" />

Keep the sky clear above it. Growth is faster at night. Starmetal Ore below speeds growth but may revert to Iron Ore.

Harvest with a pickaxe at **stage 5** for one Aberrant Crystal. Breaking it earlier yields nothing.

<Row gap="0">
  <Column alignItems="center">
    <BlockImage id="appliedas:astral_fluix_cluster" p:stage="0" scale="2" />

    1
  </Column>
  <Column alignItems="center">
    <BlockImage id="appliedas:astral_fluix_cluster" p:stage="1" scale="2" />

    2
  </Column>
  <Column alignItems="center">
    <BlockImage id="appliedas:astral_fluix_cluster" p:stage="2" scale="2" />

    3
  </Column>
  <Column alignItems="center">
    <BlockImage id="appliedas:astral_fluix_cluster" p:stage="3" scale="2" />

    4
  </Column>
  <Column alignItems="center">
    <BlockImage id="appliedas:astral_fluix_cluster" p:stage="4" scale="2" />

    5
  </Column>
</Row>

## Growing and splitting

A single crystal in Liquid Starlight has a chance to consume the fluid and increase its Size.

<Recipe id="appliedas:liquid_starlight/grow_astral_fluix_crystal" />

Two crystals in the same pool merge, losing some attributes.

<Recipe id="appliedas:liquid_starlight/merge_astral_fluix_crystals" />

Drop a crystal and use a **Starmetal Chisel** to split it, then grow the pieces separately. Splitting requires at least 2 total attribute tiers; Fortune reduces losses. An [Automatic Starmetal Chisel](chisel.md) can do this for you.

## Crystal blocks

Combine any **9 Aberrant Crystals** into a block. It does not retain crystal attributes. With Extended AE installed, it can be [sliced into circuit boards](processor.md).

<Recipe id="appliedas:astral_fluix_block" />
