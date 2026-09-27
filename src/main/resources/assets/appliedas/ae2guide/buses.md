---
navigation:
  title: Transferring Lumen with Buses
  parent: appliedas:index.md
  position: 20
  icon: ae2:import_bus
---
# Transferring Lumen with Buses

ME buses can transfer lumen between an ME network and a Lumen Array. **The bus must touch the bottom of the array**: place an ME cable below the array and mount the bus on the cable's upper face. The top and sides of the array do not support lumen transfer.

## What each bus does

- **ME Import Bus**: array → ME network. Extracts lumen from the array and stores it in Lumen Storage Cells.
- **ME Export Bus**: ME network → array. Supplies the selected lumen type to the array.
- **ME Storage Bus**: exposes the array as external storage on the ME network. Terminals can display and use its lumen while the lumen remains in the array.

## Setting a lumen filter

1. Open the bus interface.
2. Pick up a crystal of the desired lumen type with your cursor, then **right-click a filter slot**.
3. Make sure the slot shows the lumen icon, not the crystal item. Setting the filter does not consume the crystal.

An ME Export Bus needs a filter specifying which lumen to export. Left-clicking the filter slot selects the crystal item; right-clicking selects the lumen it represents.

## Why does the array still contain lumen?

Normally, an array stops allowing extraction when it contains less than **2,000 Lm**. For example, if extracting 500 Lm leaves 1,500 Lm, further transfers will stop. This is the array's extraction rule, not a bus malfunction.

Make sure the network is powered, the bus has an available channel, and the destination has storage space. ME Import and Export Buses accept Acceleration Cards to increase transfer speed. See [Troubleshooting](troubleshooting.md) if transfers do not work.
