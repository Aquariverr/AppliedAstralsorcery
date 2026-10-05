---
navigation:
  title: 星能嬗变室
  parent: appliedas:index.md
  position: 28
  icon: appliedas:starlight_transmutation_chamber
item_ids:
  - appliedas:starlight_transmutation_chamber
---
# 星能嬗变室

自动执行星辉魔法的**星能物品嬗变**配方。

<Recipe id="appliedas:starlight_transmutation_chamber" />

## 使用

1. 从任意面连接 ME 网络，供电并提供一个频道。
2. 提供配方所需星座的星能：放在降星点中心的竖直列中，夜间保持上方无遮挡；或用**链接工具**连接正常工作的聚焦水晶石或透镜。也可使用[星能 P2P 通道](starlight_p2p.md)。
3. 将材料放入输入槽，或用漏斗、ME 输出总线、样板供应器输入。产物自动存入 ME，无法存入时留在输出槽。

星能中断、网络离线或输出槽空间不足时暂停，条件恢复后继续。

## 加工时间

| 供能方式 | 默认耗时 |
| --- | --- |
| 降星点 | 1200 tick（60 秒） |
| 聚焦水晶石或透镜 | 200 tick（10 秒） |
| 流明超频 | 20 tick（1 秒） |

P2P 传输不改变耗时；两种星能同时可用时，优先使用满足配方要求的水晶或透镜供能。

开启**流明超频**后，每次开始加工时消耗 **5 Lm 永时流明**。流明不足时按原耗时加工。

## 自动拉取

从 JEI 拖入物品，或手持物品左键点击**库存配置**标记，设置对应输入槽的材料；中键设置数量。

开启**自动拉取**后，机器从 ME 补齐材料，并存回多余或种类不符的物品。
