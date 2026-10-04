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

星能嬗变室自动执行星辉魔法的**星能物品嬗变**配方。

<Recipe id="appliedas:starlight_transmutation_chamber" />

## 使用

从任意面连接 ME 网络，确保供电正常且有一个可用通道。为配方提供星能；指定星座的配方须使用对应星座的星能。可选以下方式：

- 将嬗变室放在**降星点中心所在的竖直列**，夜间保持上方无遮挡，即可直接使用星能，无需水晶石。
- 用**链接工具**将**星能聚焦水晶石**连接到嬗变室，也可通过透镜转接。水晶石须与降星点的星座共鸣、处在该光柱中心并露天，在夜间星座出现时供能；光束路径须保持畅通。

将材料放入输入槽，也可用漏斗、ME 输出总线或样板供应器从任意面输入。产物自动送入 ME 网络；网络空间不足时留在输出槽。

默认情况下，直接使用降星点星能时，每次耗时 **1200 tick（60 秒）**；使用水晶及其他传输星能时，每次耗时 **200 tick（10 秒）**。两种来源同时可用时，优先使用满足配方星座要求的传输星能。在本地单人世界中，可通过 **模组 → Applied Astralsorcery → 配置 → 服务端设置 → 机器加工时间** 调整。也可编辑 `config/appliedas-server.toml` 中的 `processing.transmutationFocalTicks` 和 `processing.transmutationStarlightTicks`；存档中已有的 `serverconfig/appliedas-server.toml` 会优先使用。

输出槽无法容纳产物、ME 网络离线或星能中断时，嬗变暂停，条件恢复后继续。

## 自动拉取

从 JEI 拖入物品或手持物品左键点击**库存配置**标记设置材料，中键点击设置数量。九个标记分别对应九个输入槽。

开启**自动拉取**后，机器按配置从 ME 补充材料，并将多余或种类不符的物品存回 ME。

## 流明超频

开启**流明超频**后，每次开始嬗变时从 ME 消耗 **5 Lm 永时流明**，默认耗时 **20 tick（1 秒）**，可通过同一配置文件中的 `processing.transmutationOverclockTicks` 调整；流明不足时按当前星能来源对应的时间进行。
