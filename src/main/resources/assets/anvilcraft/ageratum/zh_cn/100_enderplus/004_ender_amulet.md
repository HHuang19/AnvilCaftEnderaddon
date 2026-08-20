---
navigation:
  title: "末影护符"
  icon: "anvilcraft_enderplus:ender_amulet"
items:
  - anvilcraft_enderplus:ender_amulet
---

# 末影护符

<item id="anvilcraft_enderplus:ender_amulet"/>

# 获取方式

末影护符需要通过铁砧工艺的 **宝石合成**（Jewel Crafting）获得：

<recipe id="anvilcraft_enderplus:ender_amulet"/>

# 绑定护符柱

1. 手持末影护符，**潜行右键** 一根已放置的 <ref item="anvilcraft_enderplus:ender_amulet_pillar"/>，把护符绑定到该柱子
2. 绑定后，携带该护符（主手 / 副手 / Curios 护身符槽）的玩家，会获得柱子所挂护符的全部效果
3. 对 **空气右键**：解除当前绑定
4. 绑定后的护符会带有附魔流光，悬浮提示会显示绑定的护符柱坐标与维度

# 跨维度生效

- 携带已绑定的护符后，只要护符柱所在区块被加载，护符效果就会持续作用于玩家
- 配置项 `crossDimensionChunkLoad`（默认开启）会在玩家持有护符期间 **强加载** 对应区块，实现真正的跨维度随身携带

<tip>
把常用护符（药水、伤害免疫等）挂满护符柱，再绑定一枚末影护符随身携带，就相当于把整座"护符仓库"装进口袋
</tip>

# 其他

- 触发的是铁砧工艺的 AmuletEvent，效果与直接携带原护符完全一致
- 玩家登出或停止持有护符时，会自动释放强加载的区块