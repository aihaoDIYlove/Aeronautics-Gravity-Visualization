package icu.dreamripples.aero_suite.common.config;

import net.createmod.catnip.config.ConfigBase;

/**
 * Aeronautics Suite 的 COMMON 配置树(挂在 gravity_visualization 名下, 三个 mod 共享一份)。
 * 结构与配置页 UI 的树一一对应:
 *
 * <pre>
 * 航空学：重力可视化 ✓
 *  ├─ 启用火花魔杖 ✓
 *  ├─ 启用便携图解 ✓
 *  ├─ 启用配重系列方块 ✓
 *  └─ 启用配轻系列方块 ✓
 * 航空学：方便物品 ✓
 *  ├─ 启用更方便的模拟传动器 ✓
 *  ├─ 启用变速式便携引擎 ✓
 *  └─ 启用顺序供料器 ✓
 * 航空学：星空物流 ✓
 *  ├─ 启用自稳定方块 ✓
 *  ├─ 启用世界锚点 ✓
 *  ├─ 启用激活的末影珍珠 ✓
 *  ├─ 启用寻址牌 ✓
 *  ├─ 启用星空液体增产相关配方 ✓
 *  └─ 启用星空液体转换相关配方 ✓
 * </pre>
 *
 * <p>语义(与玩家约定的"获取即删"方案):
 * <ul>
 *   <li><b>物品开关</b>(火花魔杖/图解/配重/配轻/传动器/引擎/供料器/稳定器/锚点/珍珠/寻址牌):
 *       关闭 = 对应合成配方消失({@link FeatureEnabledCondition}) + 玩家获取到物品的瞬间被删除
 *       (见 FeatureGates 扫描器)。已放置的方块不受影响。</li>
 *   <li><b>配方开关</b>(增产/转换): 只关配方, 产物均为 vanilla 物品, 不涉及删除。</li>
 * </ul>
 *
 * <p>用 COMMON 而非 SERVER: 配方条件在(服务端)数据包加载时求值, COMMON 在服务器数据包
 * 加载前就绪, 时序安全; 且 COMMON 对所有存档全局生效, 符合"整套内容开关"的语义。
 */
public class AeroSuiteConfig extends ConfigBase {

    public final GravityGroup gravity = this.nested(0, GravityGroup::new, "航空学：重力可视化");
    public final SimplificationGroup simplification = this.nested(0, SimplificationGroup::new, "航空学：方便物品");
    public final StarlightGroup starlight = this.nested(0, StarlightGroup::new, "航空学：星空物流");

    @Override
    public String getName() {
        return "aero_suite";
    }

    public static class GravityGroup extends ConfigBase {
        public final ConfigBool sparkWand = this.b(true, "spark_wand", "启用火花魔杖");
        public final ConfigBool portableDiagram = this.b(true, "portable_diagram", "启用便携图解");
        public final ConfigBool counterweight = this.b(true, "counterweight", "启用配重系列方块");
        public final ConfigBool counterweightLight = this.b(true, "counterweight_light", "启用配轻系列方块");

        @Override
        public String getName() {
            return "gravity_visualization";
        }
    }

    public static class SimplificationGroup extends ConfigBase {
        public final ConfigBool analogTransmission = this.b(true, "analog_transmission", "启用更方便的模拟传动器");
        public final ConfigBool variableSpeedEngine = this.b(true, "variable_speed_engine", "启用变速式便携引擎");
        public final ConfigBool sequentialFeeder = this.b(true, "sequential_feeder", "启用顺序供料器");

        @Override
        public String getName() {
            return "simplification_related";
        }
    }

    public static class StarlightGroup extends ConfigBase {
        public final ConfigBool stabilizer = this.b(true, "stabilizer", "启用自稳定方块");
        public final ConfigBool worldAnchor = this.b(true, "world_anchor", "启用世界锚点");
        public final ConfigBool activatedPearl = this.b(true, "activated_pearl", "启用激活的末影珍珠");
        public final ConfigBool addressingSign = this.b(true, "addressing_sign", "启用寻址牌");
        public final ConfigBool multiplicationRecipes = this.b(true, "multiplication_recipes", "启用星空液体增产相关配方(红石、萤石、经验块、烈焰棒等增产复制配方)");
        public final ConfigBool conversionRecipes = this.b(true, "conversion_recipes", "启用星空液体转换相关配方(墨囊变荧光墨囊、燧石变海晶碎片等转换配方)");

        @Override
        public String getName() {
            return "starlight_logistics";
        }
    }
}
