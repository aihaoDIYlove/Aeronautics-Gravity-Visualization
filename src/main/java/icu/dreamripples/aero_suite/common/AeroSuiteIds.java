package icu.dreamripples.aero_suite.common;

/**
 * 一个 jar 三个 mod 的 id 常量。物品/方块/成就归属 = 注册 id 的 namespace,
 * 三个 mod 分别是: 重力可视化 / 方便物品 / 星空物流。
 */
public final class AeroSuiteIds {
    /** mod1: 航空学：重力可视化(火花魔杖/便携图解/配重配轻)。 */
    public static final String GRAVITY_ID = "gravity_visualization";
    /** mod2: 航空学：方便物品(模拟传动器/变速引擎/顺序供料器)。 */
    public static final String SIMPLIFICATION_ID = "simplification_related";
    /** mod3: 航空学：星空物流(星空液体/玻璃/机壳/稳定器/锚点/寻址牌/珍珠)。 */
    public static final String STARLIGHT_ID = "starlight_logistics";

    private AeroSuiteIds() {}
}
