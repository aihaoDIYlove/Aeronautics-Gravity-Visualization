<p align="center">
  <img src=".github/banner.webp" alt="Aeronautics Gravity" width="640"/>
</p>

<h1 align="center">Aeronautics：Gravity Visualization</h1>

<p align="center">
  <b>Create Aeronautics</b> 附属模组包,适用于 Minecraft 1.21.1 / NeoForge。<br/>
  本 mod 内一共 三个子 mod -- 重力可视化、方便物品、星空物流。重力可视化专注于简化载具配平；方便物品添加了一些能够提升游戏体验的机器；星空物流专注于跨界包裹/玩家传送。
</p>

<p align="center">
  <a href="README.md">English</a> | 简体中文
</p>

---

## 一个 jar，三个 mod

~~其实是做着做着跑题了，什么都想加一点~~

本套件以单个 jar 注册 **三个 mod** -- 各自可独立启用/停用，但共享 mixin 与配置:

### 1. 航空学:重力可视化(`gravity_visualization`)
**看见并调校重心** 火花魔杖可在 contraption 上叠加显示每个方块的质量数字与质心,兼作 Create 护目镜，配重块/配“轻”块支持调节质量与浮力。

### 2. 航空学:方便物品(`simplification_related`)
**省心的机械** 更方便的模拟传动器把 0-15 红石信号转成固定 RPM；变速式便携引擎提供 15 档转速；顺序供料器是可编程的批量投料磁带，服务于序列化产线。

### 3. 航空学:星空物流(`starlight_logistics`)
**简化物流** ~~（甚至“人流”）~~ 世界锚点允许你从任意两点间传送包裹，寻址牌可方便快捷地切换目标地址，激活的末影珍珠在被打包进包裹后可融入包裹物流体系，当含有其的包裹处于实体状态时，3秒后包裹自行破裂，释放末影珍珠传送玩家。

### 游戏内配置

三个 mod 的 mod 列表按钮都挂了**配置页**。每个功能、每个配方组都可独立开关(默认全开)。停用某功能会去掉对应的合成配方,并在玩家获取到该物品的瞬间删除(已放置的方块照常工作;删除时 actionbar 会给玩家提示)。

## 依赖

| 模组 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | ≥ 21.1.235 |
| Create | ≥ 6.0.10 |
| Sable | ≥ 2.0.0 |
| Simulated | ≥ 1.3.0 |
| Aeronautics | ≥ 1.3.0 |

## 安装

将 jar 文件放入客户端/服务器的 `mods/` 文件夹,连同上述依赖一起。

## 构建

```bash
./gradlew build
```

输出 jar 位于 `build/libs/`。

## 许可证

基于 [MIT License](LICENSE) 发布。
