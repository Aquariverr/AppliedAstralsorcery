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

将福鲁伊克斯水晶与其他材料投入星能液，培育异辉水晶石。

## 获取

在实心地面上准备星能液池，将配方中的材料一起投入：

<Recipe id="appliedas:liquid_starlight/form_astral_fluix_cluster" />

材料会消耗所在的一格星能液，形成异辉水晶簇。

## 生长

水晶簇共有五个生长阶段，**第 5 阶段成熟后才能采集**。

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

水晶簇需要开阔的天空，夜间生长更快。

下方的星辉金属矿石能加快生长，但可能变回铁矿石。

等水晶簇完全长成，再用镐破坏，可得 1 颗异辉水晶石。

过早采集无法得到水晶石。

## 培育

异辉水晶石具有尺寸、纯度、抛光等特质。

单独浸入星能液池，消耗星能液后有机会增大尺寸。

<Recipe id="appliedas:liquid_starlight/grow_astral_fluix_crystal" />

同一池中的两颗异辉水晶石会融合，并损失部分特质。

<Recipe id="appliedas:liquid_starlight/merge_astral_fluix_crystals" />

## 分割

先让水晶石生长，再丢在地上，用星辉金属凿子敲击。

分割后仍是异辉水晶石，特质会分散并有所损耗。

过于脆弱的水晶石无法分割，时运能减轻损耗。

将分割后的水晶石分开放入星能液池，便能继续培育。

## 水晶石块

在工作台中放满任意 9 颗异辉水晶石，即可合成异辉水晶石块。尺寸、纯度和抛光不同的水晶可以混用，合成后的方块不保留这些属性。

<Recipe id="appliedas:astral_fluix_block" />

安装 Extended AE 后，电路切片机可以将 1 个方块切成 9 个[异辉电路板](processor.md)，无需压印模板。切片产量固定，不享受压印机的尺寸增产。

[返回应用星辉](index.md)
