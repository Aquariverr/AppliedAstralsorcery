---
navigation:
  title: Aberrant Processor
  parent: appliedas:index.md
  position: 6
  icon: appliedas:astral_processor
item_ids:
  - appliedas:starlight_mysterious_cube
  - appliedas:astral_processor_press
  - appliedas:printed_astral_processor
  - appliedas:astral_processor
---
# Aberrant Processor

Awaken a mysterious cube with starlight, then inscribe an [Aberrant Crystal](crystal.md) into circuit boards.

## Obtain the press

Direct starlight at a **Mysterious Cube** or **Not So Mysterious Cube**. Starlight transmutation turns either into a **Starlight Mysterious Cube**.

Mine it with a pickaxe to obtain one **Aberrant Inscriber Press** and one [Lumen Inscriber Press](lumen_processor.md). Silk Touch and Fortune do not change these drops.

## Print circuit boards

Place the press in either outer slot of an AE2 Inscriber and an Aberrant Crystal in the middle slot.

Each operation consumes one crystal and retains the press. Only Size affects the number of boards; Purity and Cut do not.

Size 0 yields 1 board. Each additional Size level adds 1 board, up to 64.

| Crystal Size | Circuit boards |
| --- | --- |
| 0 | 1 |
| 1 | 2 |
| 2 | 3 |
| 7 | 8 |
| 62 | 63 |
| 63 or higher | 64 |

<Recipe id="appliedas:inscriber/astral_processor_print_size_0" />

The output slot must have room for the entire batch. A missing Size attribute counts as Size 0.

## Assemble the processor

Place an Aberrant Circuit Board and Printed Silicon in the outer slots, with Redstone Dust in the middle. This consumes one of each material and produces one Aberrant Processor.

<Recipe id="appliedas:inscriber/astral_processor" />

## Extended AE automation

With Extended AE installed, the Circuit Slicer turns 1 [Aberrant Crystal Block](crystal.md) into 9 Aberrant Circuit Boards without a press. Each block is crafted from any 9 Aberrant Crystals; its fixed slicing yield does not depend on the original crystals' Size.

The Crystal Assembler consumes 4 Aberrant Circuit Boards, 4 Printed Silicon and 4 Redstone Dust to produce 4 Aberrant Processors. No fluid is required.

[Back to Applied Astralsorcery](index.md)
