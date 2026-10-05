---
navigation:
  title: 祭坛自动化
  icon: appliedas:altar_automation_interface
  position: 80
  parent: appliedas:index.md
item_ids:
  - appliedas:altar_automation_interface
---
# 祭坛自动化接口

<Recipe id="appliedas:altar_automation_interface" />

合成时，用共振星杖启动虹彩合成祭坛，将[稳定遗物](artifact.md)丢在祭坛附近。

## 使用

1. 搭好祭坛和聚星转继台，清空它们的物品槽。将接口贴着 **ME 样板供应器**放置，与祭坛相距不超过 **16 格**。
2. 编码一次合成的**加工样板**，包括九宫格、转继台、投掷材料及额外流体和流明。从 JEI 转移配方可自动填写；九宫格和转继台中的流体材料仍用装满流体的容器。
3. 将样板放入供应器，在 ME 终端请求合成。接口自动放料、启动祭坛，并将产物和剩余物品送回供应器。无法送回的物品可用 ME 输入总线从接口提取。

接口只选择最近的已加载祭坛。**空手右键**查看目标和状态。祭坛等级、夜晚、焦点和星能条件仍需满足；流体和流明也可由[圣杯](chalice.md)与[阵列](lumen_array.md)另行提供。

不支持替换祭坛本体的配方。加工时勿改动祭坛和转继台材料；拆除接口会丢失其中的流体和流明。
