# Applied Astralsorcery

### Homepage [CurseForge](https://www.curseforge.com/minecraft/mc-mods/appliedas)

<img src="src/main/resources/logo.png" alt="Applied Astralsorcery logo" width="160">

Applied Astralsorcery is an addon for **Astral Sorcery** and **Applied Energistics 2 (AE2)** that lets ME networks store and transfer lumen, and adds ME network support for Lumen Arrays, Lumen Filaments, and Chalices.

For **Minecraft 1.21.1 / NeoForge**.

Licensed under the [MIT License](LICENSE).

## Features

- **ME Resonating Wand**: Retains the original wand functions and builds Astral Sorcery multiblocks. Sneak-right-click a Wireless Access Point to bind, then a structure core to build. Right-click air to configure ME or inventory item sourcing, Liquid Starlight pool building, and replacement of existing blocks with recovery into ME.
- **Lumen storage**: Available in 1k, 4k, 16k, 64k, and 256k capacities, ME Lumen Storage Cells can be installed in an ME Drive or ME Chest, with stored amounts visible in an ME Terminal. Each cell stores up to 5 lumen types by default.
- **Artifact enhancement**: A 256k cell can be enhanced up to 10 times. Each enhancement doubles its capacity and adds room for one more lumen type, allowing up to 15 types when fully enhanced.
- **Bus support**: AE2's ME Import Bus, ME Export Bus, and ME Storage Bus support lumen, allowing you to collect lumen, replenish supplies, and connect external storage, respectively.
- **ME Lumen Filament**: Collects a selected lumen type from Astral Sorcery's lumen network and stores it in ME. Connections can be relayed through regular Lumen Filaments, with a maximum range of 16 blocks per connection and a clear transmission path required.
- **ME Lumen Array**: Retains lumen generation and can automatically restock Liquid Starlight and catalysts from ME. Set a reserve amount to replenish lumen from the network when it falls below the target, and enable export to return surplus lumen to the network.
- **ME Chalice**: Retains its 64,000 mB fluid capacity and can draw fluid from ME to maintain a configured reserve or return surplus fluid to the network.
- **JEI lumen filters**: Drag any lumen ingredient from JEI into ME Import Bus, Export Bus, or Storage Bus filter slots. Works with JEI alone and with AE2 JEI Integration installed.

## Requirements

Requires Minecraft **1.21.1**, NeoForge **21.1.238 or later**, and **Java 21**. Install compatible versions of the following mods for this platform:

| Required mod | Version requirement |
| --- | --- |
| [Astral Sorcery](https://www.curseforge.com/minecraft/mc-mods/astral-sorcery) | 2.0.0 or later |
| [Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2) | 19.2.17 or later, below 20 |
| [GuideME](https://www.curseforge.com/minecraft/mc-mods/guideme) | 21.1.1 or later |
| [ObserverLib](https://www.curseforge.com/minecraft/mc-mods/observerlib) | 1.10.3 or later |
| [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) | 9.5.1 or later |

JEI, Jade, and AE2 JEI Integration are optional.

## Development checks

Run `./gradlew build` for the release JAR. Run `./gradlew -PwandGameTests runGameTestServer` for the wand's GameTests (materials, fluids, interrupted construction, saved refunds, linking, recipe, and native wand recognition). Tests use an isolated world under `build/wand-gametest-run`; test classes and templates are excluded from normal builds.

Run `./gradlew -PwandGameTests runWandPreviewClient` to capture the settings screen in Chinese and English under `build/wand-preview-run/screenshots`, without opening a world.
