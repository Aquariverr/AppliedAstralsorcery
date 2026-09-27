---
navigation:
  title: Troubleshooting
  parent: appliedas:index.md
  position: 40
  icon: ae2:certus_quartz_wrench
---
# Troubleshooting

## Check the ME network and Lumen Arrays first

1. **Power and channels**: the ME network must be powered, cables must be connected correctly, and devices must have available channels.
2. **Storage space**: install a Lumen Storage Cell with enough free capacity. Unenhanced cells hold up to **5 lumen types**. Each enhancement on a 256k cell adds **1 type**, up to **15 types**. At the type limit, a cell can still accept types it already contains, but cannot accept new ones.
3. **Lumen type**: the bus filter or ME Lumen Filament's selected type must match the lumen in the array. If you partitioned the cell in a Cell Workbench, make sure its settings allow that type.
4. **Array reserves**: an array normally stops allowing extraction below **2,000 Lm**. Let it accumulate lumen, then check the transfer again.

## A bus is not transferring lumen

- The bus must touch the **bottom of the array**. The top and sides do not work.
- Pick up a lumen crystal with your cursor and **right-click a filter slot** to select its lumen. Left-clicking selects the crystal item.
- When exporting, the array must be able to hold that lumen type and must not be full.
- Check the bus's redstone settings.

## An ME Lumen Filament is not collecting lumen

Right-click the filament to open its configuration screen, then check the displayed status:

- **Select a lumen type**: choose a type from the lumen list. If you just clicked "Stop requests", selecting a type resumes requests.
- **ME network offline**: check power, cables, and available channels.
- **Waiting for storage space**: check free cell capacity, the type limit (normally 5, increased by 1 per enhancement on a 256k cell, up to 15), and partition settings. Make sure the network can accept the lumen buffered in the filament.
- **No available path or source**: check the array's lumen type and reserves. Each connection between arrays and filaments must be no longer than 16 blocks, with no obstructions.
- **Receiving**: lumen is being received and the filament is working normally.
- **Waiting to request**: a type is selected and the filament is waiting for its next request check.

## The network shows lumen, but the cell is empty

An **ME Storage Bus** exposes an array as external storage, so the amount shown in the terminal may come from the array. To move lumen into a cell, use an **ME Import Bus** or **ME Lumen Filament**.
