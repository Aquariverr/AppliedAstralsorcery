---
navigation:
  title: 制作存储元件
  parent: appliedas:index.md
  position: 10
  icon: appliedas:lumen_storage_cell_1k
item_ids:
  - appliedas:lumen_cell_housing
  - appliedas:lumen_storage_component_1k
  - appliedas:lumen_storage_cell_1k
---
# 制作存储元件

流明存储元件由**流明存储组件**和**流明元件外壳**合成，放入 ME 驱动器或 ME 箱子后即可使用。它存储的是流明；流明水晶属于物品，不能直接转化为元件中的流明。

## 1. 合成流明元件外壳

<Recipe id="appliedas:lumen_cell_housing" />

## 2. 制作 1k 流明存储组件

使用**共振工作台**，并按星芒宝典搭建包含外圈聚星转继台的祭坛结构。中心使用**流明处理器**。

下图与星芒宝典的配方布局相同：上方是产物，中央九格是工作台内的材料，外围物品放在对应位置的**聚星转继台**上。鼠标悬停可查看材料名称。

<Recipe id="appliedas:lumen_storage_component_1k" />

摆好材料后，在**夜间**用**共振星杖**右键工作台开始合成。还需将 **2 颗海蓝宝石**丢在工作台附近，供合成过程消耗。满足条件后，合成需要 **5 秒**。

也可以使用**普通工作台**：按照 AE2 的 1k ME 存储组件布局，四角放红石，上下左右放赛特斯石英，中心改为**异辉处理器**，得到同一种 1k 流明存储组件。

<Recipe id="appliedas:lumen_storage_component_1k_from_astral_processor" />

## 3. 合成 1k ME 流明存储元件

将组件和外壳放入合成栏，位置不限。

<Recipe id="appliedas:lumen_storage_cell_1k" />

## 容量与类型

未强化的流明存储元件最多存储 **5 种流明**。即使还有剩余容量，已有 5 种时也不能再加入第 6 种；取空其中一种后即可存入其他类型。

1k 元件只存一种流明时，最多存储 **5,080 Lm**。每增加一种流明都会占用部分字节，因此混合存储时可用容量会减少。

空元件可手持 **Shift + 右键**，拆回组件和流明元件外壳。

256k 元件的强化方法和配方见[容量强化](enhancement.md)。
