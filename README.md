# Applied Astralsorcery

<img src="src/main/resources/logo.png" alt="Applied Astralsorcery logo" width="400">

An addon for **Astral Sorcery** and **Applied Energistics 2 (AE2)** that lets ME networks store and transfer lumen and automate Astral Sorcery devices.

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/appliedas) · [MIT License](LICENSE)

## Features

- **ME Celestial Gateway**: Travel through the original gateway star interface to ordinary gateways or inserted AE2 Spatial Storage Cells. Ordinary gateways can also enter cells directly; each cell star uses the cell's name. Every stored dimension must exceed 5 blocks, and unformatted 16³/128³ cells are formatted automatically. A compact return portal leads back to the associated ME gateway without requiring a multiblock. ME gateway blocks themselves are not destinations.

- **ME Lumen Storage Cells**: Store up to 5 lumen types in 1k, 4k, 16k, 64k or 256k cells, with contents visible in an ME Terminal. A 256k cell supports up to 10 artifact enhancements, each doubling capacity and adding one lumen type, for a maximum of 15 types.
- **ME buses**: Import, export and connect external lumen storage using AE2's ME Import Bus, ME Export Bus and ME Storage Bus. Drag lumen ingredients from JEI into their filter slots, with or without AE2 JEI Integration.
- **ME Lumen Filament**: Collect a selected lumen type from Astral Sorcery's lumen network into ME. Connections can pass through regular Lumen Filaments; each connection requires a clear path and has a 16-block range.
- **ME Lumen Array**: Generate lumen, restock Liquid Starlight and catalysts from ME, and maintain a lumen reserve by drawing from or returning surplus to the network.
- **ME Lumen Alchemy Array**: Automate native combination recipes using catalysts, Liquid Starlight and ingredient lumen from ME. Retains the original 4,000 Lm / 2,000 mB capacities, with a configurable output reserve and ME filament interaction. Provides lumen to Astral Sorcery consumers without requesting it from other sources.
- **ME Chalice**: Store 64,000 mB of fluid and maintain a configured reserve using ME, with optional surplus export.
- **ME Resonating Wand**: Build Astral Sorcery multiblocks using materials from ME and your inventory. Supports Liquid Starlight pools and block replacement with recovery into ME, alongside the original wand functions.
- **Aberrant Crystal**: Form a five-stage cluster from Stardust, a Singularity and a Fluix Crystal in Liquid Starlight. Harvest crystals with Astral Sorcery's Size, Purity and Cut attributes, then grow, merge or split them.
- **Aberrant Processor and Lumen Processor**: Obtain their Inscriber Presses from a Starlight Mysterious Cube and use them to process Aberrant Crystals or Lumen Crystals. Aberrant Crystal Size determines the number of circuit boards produced.
- **Constellation Core**: Combine crystals attuned to all 12 constellations on an Iridescent Crafting Altar. Each constellation accepts either a rock or celestial crystal.
- **Altar Automation Interface**: Connect an ME Pattern Provider to the nearest loaded altar within 16 blocks. Processing patterns supply items, additional fluids and lumen, with JEI transfers including all three. The interface buffers fluids and lumen; products and container returns go back to the provider. Astral Sorcery's altar conditions still apply.
- **Automatic Starmetal Chisel**: Process crystals, Starmetal Ingots and artifacts from item slots or adjacent dropped items. Each operation takes 2 seconds and 25 Lm of Evorsio and follows Astral Sorcery's processing rules. Supports side configuration, automatic item transfers, enchantments, lumen relays and ME Export Buses.
- **Starlight Transmutation Chamber**: Automate native starlight item transmutation recipes in a gilded marble and quartz glass enclosure. Link a Collector Crystal or Lens, supply ingredients manually or through item automation, and return products directly to ME. Respects recipe durations and constellation requirements, with saved progress and buffered output when storage is full.
- **Non-Empty Annihilation Plane**: Collect blocks like AE2's Annihilation Plane, but leave a block intact if its loot contains no items. Fluid and dropped-item collection work as usual.

Recipes and usage instructions are available in the in-game AE2 guide in English and Chinese, with recipes also viewable in JEI.

## Requirements

Minecraft **1.21.1**, NeoForge **21.1.238 or later**, and **Java 21**, with compatible versions of these mods:

| Required mod | Version |
| --- | --- |
| [Astral Sorcery](https://www.curseforge.com/minecraft/mc-mods/astral-sorcery) | 2.0.0 or later |
| [Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2) | 19.2.17 or later, below 20 |
| [GuideME](https://www.curseforge.com/minecraft/mc-mods/guideme) | 21.1.1 or later |
| [ObserverLib](https://www.curseforge.com/minecraft/mc-mods/observerlib) | 1.10.3 or later |
| [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) | 9.5.1 or later |

JEI, Jade and AE2 JEI Integration are optional.

Jade shows AE2's native network status for the Starlight Transmutation Chamber, ME Lumen Array and ME Chalice, including online, offline, network booting and missing channels.
