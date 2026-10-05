---
navigation:
  title: 异辉水晶石
  parent: appliedas:index.md
  position: 5
  icon: appliedas:astral_fluix_crystal
item_ids:
  - appliedas:astral_fluix_crystal
  - appliedas:astral_fluix_cluster
  - appliedas:astral_fluix_block
---
# 异辉水晶石

## 获取

在实心地面上准备星能液，将配方材料一起投入，消耗一格星能液形成水晶簇。

<Recipe id="appliedas:liquid_starlight/form_astral_fluix_cluster" />

保持上方无遮挡，夜间生长更快。下方放星辉金属矿石可加速，但矿石可能变回铁矿石。

**第 5 阶段成熟后**用镐采集，得到 1 颗异辉水晶石；提前破坏无产物。

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

## 培育与分割

单颗水晶浸入星能液，有机会消耗星能液增大尺寸。

<Recipe id="appliedas:liquid_starlight/grow_astral_fluix_crystal" />

两颗水晶在同一池中会融合，并损失部分属性。

<Recipe id="appliedas:liquid_starlight/merge_astral_fluix_crystals" />

将水晶丢在地上，用**星辉金属凿子**分割，再分开培育。属性总等级不足 2 时无法分割；时运可减少损耗。也可使用[自动星辉金属凿子](chisel.md)。

## 水晶石块

任意 **9 颗异辉水晶石**可合成水晶石块，方块不保留水晶属性。安装 Extended AE 后，可用于[切片制作电路板](processor.md)。

<Recipe id="appliedas:astral_fluix_block" />
