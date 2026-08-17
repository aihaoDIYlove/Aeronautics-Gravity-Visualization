package icu.dreamripples.aero_suite.common.config;

import icu.dreamripples.aero_suite.common.AeroSuite;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import icu.dreamripples.aero_suite.common.registry.ModItems;
import net.minecraft.world.item.BlockItem;
// import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.HashSet;
import java.util.Set;

/**
 * 功能门控中枢: 配置开关 key 查询 + 开关 -> 受控物品集合。
 *
 * <ul>
 *   <li>{@link #isEnabled(String)} 给 {@link FeatureEnabledCondition}(配方层)用。</li>
 *   <li>{@link #disabledItems()} 给物品删除执行器(玩家获取即删)用 -- 返回所有被停用
 *       开关覆盖的 Item 集合, 服务端 tick 扫描在线玩家背包/光标, 命中即清空 + actionbar 提示。</li>
 * </ul>
 *
 * <p>注册项(DeferredHolder)在注册完成后才可 get(), disabledItems() 惰性构建并缓存,
 * 配置 reload 时 {@link #invalidate()} 失效。
 */
public final class FeatureGates {

    /** mod1: gravity_visualization 的配置实例(COMMON, 三个 mod 共享)。 */
    public static volatile AeroSuiteConfig CONFIG;

    private static volatile Set<Item> cachedDisabled = Set.of();
    private static volatile boolean anyDisabled;

    /** 全部开关 key(按配置树顺序)。 */
    public static final String[] ALL_KEYS = {
            "spark_wand", "portable_diagram", "counterweight", "counterweight_light",
            "analog_transmission", "variable_speed_engine", "sequential_feeder",
            "stabilizer", "world_anchor", "activated_pearl", "addressing_sign",
            "multiplication_recipes", "conversion_recipes",
    };

    public static boolean isEnabled(String key) {
        AeroSuiteConfig c = CONFIG;
        if (c == null) return true; // 配置未加载前按全开处理(默认值全 true, 一致)
        return switch (key) {
            case "spark_wand" -> c.gravity.sparkWand.get();
            case "portable_diagram" -> c.gravity.portableDiagram.get();
            case "counterweight" -> c.gravity.counterweight.get();
            case "counterweight_light" -> c.gravity.counterweightLight.get();
            case "analog_transmission" -> c.simplification.analogTransmission.get();
            case "variable_speed_engine" -> c.simplification.variableSpeedEngine.get();
            case "sequential_feeder" -> c.simplification.sequentialFeeder.get();
            case "stabilizer" -> c.starlight.stabilizer.get();
            case "world_anchor" -> c.starlight.worldAnchor.get();
            case "activated_pearl" -> c.starlight.activatedPearl.get();
            case "addressing_sign" -> c.starlight.addressingSign.get();
            case "multiplication_recipes" -> c.starlight.multiplicationRecipes.get();
            case "conversion_recipes" -> c.starlight.conversionRecipes.get();
            default -> {
                AeroSuite.LOGGER.warn("Unknown feature gate key: {}", key);
                yield true;
            }
        };
    }

    /** 配置 load/reload 后调用: 重建缓存。 */
    public static void invalidate() {
        anyDisabled = false;
        for (String key : ALL_KEYS) {
            if (!isEnabled(key)) { anyDisabled = true; break; }
        }
        cachedDisabled = anyDisabled ? buildDisabled() : Set.of();
    }

    /** 当前是否有任何开关处于关闭状态(全开时扫描器直接短路, 零开销)。 */
    public static boolean anyDisabled() {
        return anyDisabled;
    }

    /** 停用开关覆盖的物品集合(含 BlockItem 与 incomplete_* 半成品、zinc_lump)。 */
    public static Set<Item> disabledItems() {
        return cachedDisabled;
    }

    private static Set<Item> buildDisabled() {
        Set<Item> items = new HashSet<>();
        if (!isEnabled("spark_wand")) items.add(ModItems.SPARK_WAND.get());
        if (!isEnabled("portable_diagram")) items.add(ModItems.PORTABLE_DIAGRAM.get());
        if (!isEnabled("counterweight")) {
            items.add(ModBlocks.COUNTERWEIGHT_ITEM.get());
            items.add(ModBlocks.COUNTERWEIGHT_REDSTONE_ITEM.get());
            items.add(ModItems.INCOMPLETE_COUNTERWEIGHT.get());
        }
        if (!isEnabled("counterweight_light")) {
            items.add(ModBlocks.COUNTERWEIGHT_LIGHT_ITEM.get());
            items.add(ModBlocks.COUNTERWEIGHT_LIGHT_PEARL_ITEM.get());
            items.add(ModBlocks.COUNTERWEIGHT_LIGHT_REDSTONE_ITEM.get());
            items.add(ModBlocks.COUNTERWEIGHT_LIGHT_PEARL_REDSTONE_ITEM.get());
            items.add(ModItems.INCOMPLETE_COUNTERWEIGHT_LIGHT.get());
        }
        if (!isEnabled("analog_transmission")) items.add(ModBlocks.CONVENIENT_ANALOG_TRANSMISSION_ITEM.get());
        if (!isEnabled("variable_speed_engine")) {
            for (DeferredHolder<Item, BlockItem> h : ModBlocks.VARIABLE_SPEED_PORTABLE_ENGINE_ITEMS.values()) {
                items.add(h.get());
            }
        }
        if (!isEnabled("sequential_feeder")) {
            items.add(ModBlocks.SEQUENTIAL_FEEDER_ITEM.get());
            items.add(ModItems.INCOMPLETE_SEQUENTIAL_FEEDER.get());
        }
        if (!isEnabled("stabilizer")) {
            items.add(ModBlocks.STABILIZER_ITEM.get());
            items.add(ModItems.INCOMPLETE_STABILIZER.get());
        }
        if (!isEnabled("world_anchor")) {
            items.add(ModBlocks.WORLD_ANCHOR_ITEM.get());
            items.add(ModItems.INCOMPLETE_WORLD_ANCHOR.get());
        }
        if (!isEnabled("activated_pearl")) items.add(ModItems.ACTIVATED_ENDER_PEARL.get());
        if (!isEnabled("addressing_sign")) items.add(ModBlocks.ADDRESSING_SIGN_ITEM.get());
        // 增产/转换组唯一 mod 物品: zinc_lump(合成+转换+删除三绑定同开关)
        if (!isEnabled("conversion_recipes")) items.add(ModItems.ZINC_LUMP.get());
        return Set.copyOf(items);
    }

    private FeatureGates() {}
}
