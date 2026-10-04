---
navigation:
  title: 异辉处理器
  parent: appliedas:index.md
  position: 6
  icon: appliedas:astral_processor
item_ids:
  - appliedas:starlight_mysterious_cube
  - appliedas:astral_processor_press
  - appliedas:printed_astral_processor
  - appliedas:astral_processor
---
# 异辉处理器

用星能唤醒神秘方块，再将[异辉水晶石](crystal.md)压印成电路板。

## 获取压印模板

将星能引导至 **神秘方块** 或 **没那么神秘的方块**，通过星能方块嬗变得到 **星光神秘方块**。

用镐挖掘星光神秘方块，得到 **异辉压印模板** 和 [流明压印模板](lumen_processor.md) 各 1 个。精准采集和时运不会改变掉落。

## 压印电路板

将异辉压印模板放入 AE2 压印机的上槽或下槽，将异辉水晶石放入中槽。

每次消耗 1 颗水晶石，模板可以重复使用。电路板数量只由尺寸决定，纯度与抛光不影响产量。

尺寸为 0 时产出 1 个电路板；尺寸每增加 1，产量增加 1，最多 64 个。

| 水晶尺寸 | 电路板数量 |
| --- | --- |
| 0 | 1 |
| 1 | 2 |
| 2 | 3 |
| 7 | 8 |
| 62 | 63 |
| 63 及以上 | 64 |

<Recipe id="appliedas:inscriber/astral_processor_print_size_0" />

输出槽需要容纳本次全部电路板；空间不足时压印机会等待。没有尺寸属性时按尺寸 0 计算。

## 压合处理器

在压印机中，将异辉电路板与硅板分别放入上下槽，中槽放红石粉。三种材料各消耗 1 个，得到 1 个异辉处理器。

<Recipe id="appliedas:inscriber/astral_processor" />

## Extended AE 自动化

安装 Extended AE 后，电路切片机可以将 1 个[异辉水晶石块](crystal.md)切成 9 个异辉电路板，无需压印模板。每个方块由任意 9 颗异辉水晶石合成，切片产量固定，不受原水晶尺寸影响。

水晶装配器每批消耗 4 个异辉电路板、4 个硅板和 4 个红石粉，产出 4 个异辉处理器，无需流体。

[返回应用星辉](index.md)
