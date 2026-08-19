package icu.dreamripples.aero_suite.common.config;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import icu.dreamripples.aero_suite.common.registry.ModItems;

/**
 * 功能门控元数据表: 整个 mod 的内容开关唯一定义处, 三处消费者共用 --
 * <ul>
 *   <li>{@link AeroSuiteConfig} -- 按此表生成 COMMON 配置树(ConfigBool);</li>
 *   <li>{@link FeatureGates} -- {@code isEnabled(key)}(配方条件)与 {@code disabledItems()}(获取即删);</li>
 *   <li>配置屏 UI -- 分页/标签/图标/tooltip 全部按此表渲染。</li>
 * </ul>
 *
 * <p>语义约定(与玩家约定不变):
 * <ul>
 *   <li><b>物品开关</b> {@code deletesItems() 非空}: 关 = 对应合成配方消失 + 获取即删。
 *       incomplete_* 半成品没有独立开关, 绑进其本体/所属配方的删除集。</li>
 *   <li><b>配方开关</b> {@code deletesItems() 为空}: 只关配方(产物为 vanilla/其他 mod 物品), 不删任何东西。
 *       唯一例外 recipe_levitite -- 中间体 incomplete_levitite 是本 mod 物品, 绑进其删除集。</li>
 * </ul>
 *
 * <p>lang 键: {@code feature.gravity_visualization.<key>} / {@code .desc}。
 */
public final class AeroSuiteFeatures {

    public enum Group {
        /** mod1 航空学: 重力可视化 */
        GRAVITY("gravity_visualization"),
        /** mod2 航空学: 方便物品 */
        SIMPLIFICATION("simplification_related"),
        /** mod3 航空学: 星空物流 */
        STARLIGHT("starlight_logistics");

        /** 对应配置子树名(= 归属 mod id, 亦作 UI 分页 id)。 */
        public final String id;

        Group(String id) { this.id = id; }
    }

    /**
     * @param key          配置键(同时也是配方 JSON feature_enabled 条件的 key)
     * @param group        UI 分页/配置子树
     * @param items        关闭时纳入"获取即删"的物品(空 = 纯配方开关)
     * @param icon         UI 图标物品(纯配方开关用其 vanilla 产物)
     */
    public record Feature(String key, Group group, boolean defaultValue,
                          List<Supplier<? extends Item>> items, Supplier<? extends Item> icon) {

        public boolean deletesItems() { return !items.isEmpty(); }
    }

    // ── WARNING ────────────────────────────────────────────────
    // 本表是内容门控的唯一事实源: 新增可门控物品/配方**必须**在此登记,
    // 否则该内容无开关、不会被获取即删扫描、配方不被门控 -- 全程无任何报错
    // (仅有 verifyCoverage() 的 WARN 兜底)。
    // ────────────────────────────────────────────────────────────
    public static final List<Feature> ALL = List.of(
            // ── 航空学: 重力可视化 ─────────────────────────────────
            new Feature("spark_wand", Group.GRAVITY, true,
                    List.of(ModItems.SPARK_WAND), ModItems.SPARK_WAND),
            new Feature("portable_diagram", Group.GRAVITY, true,
                    List.of(ModItems.PORTABLE_DIAGRAM), ModItems.PORTABLE_DIAGRAM),
            new Feature("counterweight", Group.GRAVITY, true,
                    List.of(ModBlocks.COUNTERWEIGHT_ITEM, ModItems.INCOMPLETE_COUNTERWEIGHT),
                    ModBlocks.COUNTERWEIGHT_ITEM),
            new Feature("counterweight_redstone", Group.GRAVITY, true,
                    List.of(ModBlocks.COUNTERWEIGHT_REDSTONE_ITEM), ModBlocks.COUNTERWEIGHT_REDSTONE_ITEM),
            new Feature("counterweight_light", Group.GRAVITY, true,
                    List.of(ModBlocks.COUNTERWEIGHT_LIGHT_ITEM, ModItems.INCOMPLETE_COUNTERWEIGHT_LIGHT),
                    ModBlocks.COUNTERWEIGHT_LIGHT_ITEM),
            new Feature("counterweight_light_pearl", Group.GRAVITY, true,
                    List.of(ModBlocks.COUNTERWEIGHT_LIGHT_PEARL_ITEM), ModBlocks.COUNTERWEIGHT_LIGHT_PEARL_ITEM),
            new Feature("counterweight_light_redstone", Group.GRAVITY, true,
                    List.of(ModBlocks.COUNTERWEIGHT_LIGHT_REDSTONE_ITEM), ModBlocks.COUNTERWEIGHT_LIGHT_REDSTONE_ITEM),
            new Feature("counterweight_light_pearl_redstone", Group.GRAVITY, true,
                    List.of(ModBlocks.COUNTERWEIGHT_LIGHT_PEARL_REDSTONE_ITEM), ModBlocks.COUNTERWEIGHT_LIGHT_PEARL_REDSTONE_ITEM),
            // 铁块 SA 赌重锤核心(产物 vanilla)
            new Feature("recipe_heavy_core", Group.GRAVITY, true,
                    List.of(), () -> Items.HEAVY_CORE),
            // ── 航空学: 方便物品 ───────────────────────────────────
            new Feature("analog_transmission", Group.SIMPLIFICATION, true,
                    List.of(ModBlocks.CONVENIENT_ANALOG_TRANSMISSION_ITEM), ModBlocks.CONVENIENT_ANALOG_TRANSMISSION_ITEM),
            // 16 色引擎整体一个开关(用户决策: 逐色太碎)
            new Feature("variable_speed_engine", Group.SIMPLIFICATION, true,
                    List.copyOf(ModBlocks.VARIABLE_SPEED_PORTABLE_ENGINE_ITEMS.values()),
                    ModBlocks.VARIABLE_SPEED_PORTABLE_ENGINE_ITEMS.values().iterator().next()),
            new Feature("sequential_feeder", Group.SIMPLIFICATION, true,
                    List.of(ModBlocks.SEQUENTIAL_FEEDER_ITEM, ModItems.INCOMPLETE_SEQUENTIAL_FEEDER),
                    ModBlocks.SEQUENTIAL_FEEDER_ITEM),
            new Feature("single_slot_hopper", Group.SIMPLIFICATION, true,
                    List.of(ModBlocks.SINGLE_SLOT_HOPPER_ITEM), ModBlocks.SINGLE_SLOT_HOPPER_ITEM),
            // ── 航空学: 星空物流 ───────────────────────────────────
            new Feature("stabilizer", Group.STARLIGHT, true,
                    List.of(ModBlocks.STABILIZER_ITEM, ModItems.INCOMPLETE_STABILIZER), ModBlocks.STABILIZER_ITEM),
            new Feature("world_anchor", Group.STARLIGHT, true,
                    List.of(ModBlocks.WORLD_ANCHOR_ITEM, ModItems.INCOMPLETE_WORLD_ANCHOR), ModBlocks.WORLD_ANCHOR_ITEM),
            new Feature("addressing_sign", Group.STARLIGHT, true,
                    List.of(ModBlocks.ADDRESSING_SIGN_ITEM), ModBlocks.ADDRESSING_SIGN_ITEM),
            new Feature("pearl_stasis", Group.STARLIGHT, true,
                    List.of(ModBlocks.PEARL_STASIS_ITEM), ModBlocks.PEARL_STASIS_ITEM),
            new Feature("activated_pearl", Group.STARLIGHT, true,
                    List.of(ModItems.ACTIVATED_ENDER_PEARL), ModItems.ACTIVATED_ENDER_PEARL),
            new Feature("lightweight_glass", Group.STARLIGHT, true,
                    List.of(ModBlocks.LIGHTWEIGHT_GLASS_ITEM), ModBlocks.LIGHTWEIGHT_GLASS_ITEM),
            new Feature("ultralight_glass", Group.STARLIGHT, true,
                    List.of(ModBlocks.ULTRALIGHT_GLASS_ITEM), ModBlocks.ULTRALIGHT_GLASS_ITEM),
            new Feature("starlight_casing", Group.STARLIGHT, true,
                    List.of(ModBlocks.STARLIGHT_CASING_ITEM), ModBlocks.STARLIGHT_CASING_ITEM),
            new Feature("void_hose_pulley", Group.STARLIGHT, true,
                    List.of(ModBlocks.VOID_HOSE_PULLEY_ITEM), ModBlocks.VOID_HOSE_PULLEY_ITEM),
            new Feature("starlight_bucket", Group.STARLIGHT, true,
                    List.of(ModItems.STARLIGHT_BUCKET), ModItems.STARLIGHT_BUCKET),
            new Feature("starlight_bottle", Group.STARLIGHT, true,
                    List.of(ModItems.STARLIGHT_BOTTLE), ModItems.STARLIGHT_BOTTLE),
            new Feature("zinc_lump", Group.STARLIGHT, true,
                    List.of(ModItems.ZINC_LUMP), ModItems.ZINC_LUMP),
            // levitite SA: 产物 aeronautics:levitite, 中间体 incomplete_levitite 绑进删除集
            new Feature("recipe_levitite", Group.STARLIGHT, true,
                    List.of(ModItems.INCOMPLETE_LEVITITE), ModItems.INCOMPLETE_LEVITITE),
            new Feature("recipe_pearlescent_levitite", Group.STARLIGHT, true,
                    List.of(), ModItems.INCOMPLETE_LEVITITE),
            // 增产组(4)
            new Feature("mult_blaze_rod", Group.STARLIGHT, true, List.of(), () -> Items.BLAZE_ROD),
            new Feature("mult_glowstone_dust", Group.STARLIGHT, true, List.of(), () -> Items.GLOWSTONE_DUST),
            new Feature("mult_redstone", Group.STARLIGHT, true, List.of(), () -> Items.REDSTONE),
            new Feature("mult_experience_block", Group.STARLIGHT, true, List.of(), () -> Items.EXPERIENCE_BOTTLE),
            // 转换组(7)
            new Feature("conv_coal", Group.STARLIGHT, true, List.of(), () -> Items.COAL),
            new Feature("conv_glow_ink", Group.STARLIGHT, true, List.of(), () -> Items.GLOW_INK_SAC),
            new Feature("conv_prismarine_crystals", Group.STARLIGHT, true, List.of(), () -> Items.PRISMARINE_CRYSTALS),
            new Feature("conv_prismarine_shard", Group.STARLIGHT, true, List.of(), () -> Items.PRISMARINE_SHARD),
            new Feature("conv_echo_shard", Group.STARLIGHT, true, List.of(), () -> Items.ECHO_SHARD),
            new Feature("conv_ender_pearl", Group.STARLIGHT, true, List.of(), () -> Items.ENDER_PEARL),
            new Feature("conv_breeze_rod", Group.STARLIGHT, true, List.of(), () -> Items.BREEZE_ROD),
            new Feature("conv_tuff", Group.STARLIGHT, true, List.of(), () -> Items.TUFF));

    private AeroSuiteFeatures() {}

    /**
     * 覆盖性自检: 扫描三个 mod namespace 下所有已注册物品, 未被任何 Feature 的 items 覆盖则 WARN。
     * 防止"新增物品/配方忘记登记进 ALL 表"的静默失效(忘记登记 = 无开关/不删除/配方不门控, 且无任何报错)。
     * 仅在 config load/reload 时调用一次, 零运行时开销。
     */
    public static void verifyCoverage() {
        Set<Item> covered = new HashSet<>();
        for (Feature f : ALL) {
            for (Supplier<? extends Item> s : f.items()) {
                covered.add(s.get());
            }
        }
        for (var entry : net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet()) {
            String ns = entry.getKey().location().getNamespace();
            if (!ns.equals(icu.dreamripples.aero_suite.common.AeroSuiteIds.GRAVITY_ID)
                    && !ns.equals(icu.dreamripples.aero_suite.common.AeroSuiteIds.SIMPLIFICATION_ID)
                    && !ns.equals(icu.dreamripples.aero_suite.common.AeroSuiteIds.STARLIGHT_ID)) continue;
            if (!covered.contains(entry.getValue())) {
                com.mojang.logging.LogUtils.getLogger().warn(
                        "[AeroSuite] 物品 {} 未登记进 AeroSuiteFeatures.ALL -- 将无配置开关/不被删除扫描/配方不门控。若有意不门控请显式忽略此 WARN。",
                        entry.getKey().location());
            }
        }
    }
}
