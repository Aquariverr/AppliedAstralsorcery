---
navigation:
  title: ME Lumen Alchemy Array
  parent: appliedas:index.md
  position: 26
  icon: appliedas:me_lumen_alchemy_array
item_ids:
  - appliedas:me_lumen_alchemy_array
---
# ME Lumen Alchemy Array

Connect ME to the **bottom** of the array. It needs power and one channel. Mark the desired combination lumen using the arrows, a lumen crystal, an ME lumen stack, or a JEI drag.

The array retains the original **4,000 Lm** output capacity, **2,000 mB** Liquid Starlight tank and single catalyst slot. With automatic supply enabled, it obtains Liquid Starlight and a matching catalyst from ME. Combination lumen ingredients are taken directly from ME when production is possible. Keep these ingredients in ME Lumen Storage Cells and catalysts in item storage.

The **reserve** sets how much selected output to pull from ME (0–4,000 Lm). With export enabled, output above the reserve returns to ME. This reserve does not limit the separate ingredients consumed by the recipe. Original fill-dependent generation, Liquid Starlight consumption, catalyst shattering and lumen discovery still apply.

**Redstone control** defaults to off, ignoring redstone signals. When enabled, only production requires a redstone signal. Without a signal, production's Liquid Starlight consumption and catalyst wear pause. Automatic ME lumen, Liquid Starlight and catalyst refills, surplus exports and lumen type changes remain available. Stored lumen remains available to external consumers.

The array **only provides** lumen to Astral Sorcery's transfer network; it never requests lumen from other arrays or filaments. The **ME filament interaction** switch controls whether ME Lumen Filaments may collect its output, including through ordinary relays. It is off by default; other native consumers can still draw lumen.

Changing the marked type first returns the old output, catalyst and any partially fetched ingredients to ME. If storage is full or offline, switching waits without discarding them. Normal production and refill resume after the return completes. Shift-use retains native fluid and catalyst insertion.

The recipe follows the ME Lumen Array layout, with a native **Lumen Alchemy Array** in the center.

<Recipe id="appliedas:me_lumen_alchemy_array" />
