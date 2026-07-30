<p align="center">
  <img src=".github/banner.webp" alt="Aeronautics Gravity" width="640"/>
</p>

<h1 align="center">Aeronautics: Gravity Visualization</h1>

<p align="center">
  <b>Create Aeronautics</b> 附属模组,适用于 Minecraft 1.21.1 / NeoForge。<br/>
  提供一系列工具与方块,用于调校 contraption 上的 Sable 物理 - 质量可视化、可配置配重块、轻质玻璃、自稳定方块等。
</p>

<p align="center">
  <a href="README.md">English</a> | 简体中文
</p>

---

## 功能

### 质量可视化(Mass Visualizer)
手持 **火花魔杖(Spark Wand)** 右键 物理化结构,可叠加显示每个方块的质量数字与质心十字准星。
- **普通模式**:显示质量 > 0 的表面方块,带遮挡剔除,数字不会穿墙扎堆。
- **重块模式**(Shift + 右键):显示所有质量 ≥ 2 的方块，以及提供浮力的方块,忽略遮挡 - 用于配平与平衡分析。

### 更方便的模拟传动器(Convenient Analog Transmission)
一个转速控制器。根据 0–15 的红石信号从查找表输出**固定 RPM**,0-15红石对应32-256RPM，忽略输入转速。信号为 0 时表现为离合器。

### 配重块(Counterweight Blocks)
按方块调校 Sable 物理属性,分两个家族:

| 家族 | 调整方式 | 方块 | 范围 |
|---|---|---|---|
| **手调** | 滚动数值面板(空手右键) | counterweight、counterweight_light(+ 皮肤) | 质量 1–20,浮力 1–36 |
| **红石** | 邻接红石信号 0–15 | counterweight_redstone、counterweight_light_redstone(+ 皮肤) | 档位 1–16 |

红石家族没有数值面板 - 其灯带按信号染色,可一眼读出档位(红 = 向下力,青 = 浮力)。护目镜也会显示当前档位。

### 轻质 / 超轻玻璃(Lightweight / Ultralight Glass)
低质量玻璃,用于从 contraption 上减重,带连接纹理(玻璃板视觉上合并)。
- **轻质玻璃(Lightweight Glass)**:0.25 kpg(原版玻璃的 ¼)。
- **超轻玻璃(Ultralight Glass)**:0.125 kpg,`fragile` - 受撞击破碎。

### 自稳定方块(Stabilizer Block)
让 物理化结构 **保持水平且不干扰偏航转向**。
- 带倾斜速度增益调度的 PD 控制器:慢倾斜时响应温和,快倾斜时提前响应(跑赢 45° 物理失效极限)。
- 根据相对质心的位置在**质量模式**(高端,向下力)与**浮力模式**(低端,向上力)之间自动切换。
- 可调死区(0–30°,每个侧面)且可由红石控制(顶/底面:无红石 / 有红石时开启)。

### 火花魔杖(Spark Wand)
质量可视化的触发器 - 同时也是一把战斗武器:
- 造成燃烧伤害(视觉燃烧,非致命击不留下残留火焰;致命击烧熟掉落物)。
- **苦力怕克星(Creeper Buster)** 附魔(附魔台):一击秒杀苦力怕。
- 手持时兼作 **Create 护目镜** - 显示动力学应力叠加与瞄准信息。

## 依赖

| 模组 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.235 |
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
