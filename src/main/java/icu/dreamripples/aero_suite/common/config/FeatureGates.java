package icu.dreamripples.aero_suite.common.config;

import icu.dreamripples.aero_suite.common.AeroSuite;
import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.Set;

/**
 * 功能门控中枢: 开关 key 查询 + 开关 -> 受控物品集合, 全部由 {@link AeroSuiteFeatures#ALL}
 * 元数据表驱动(UI / 配方条件 / 获取即删共用一份定义)。
 *
 * <ul>
 *   <li>{@link #isEnabled(String)} 给 {@link FeatureEnabledCondition}(配方层)与各处早期返回用。</li>
 *   <li>{@link #disabledItems()} 给物品删除执行器(玩家获取即删)用 -- 返回所有被停用
 *       开关覆盖的 Item 集合, 服务端 tick 扫描在线玩家背包/光标, 命中即清空 + actionbar 提示。</li>
 * </ul>
 *
 * <p>注册项(Supplier)在注册完成后才可 get(), disabledItems() 惰性构建并缓存,
 * 配置 load/reload 时 {@link #invalidate()} 失效。
 */
public final class FeatureGates {

    /** mod1: gravity_visualization 的配置实例(COMMON, 三个 mod 共享)。 */
    public static volatile AeroSuiteConfig CONFIG;

    private static volatile Set<Item> cachedDisabled = Set.of();
    private static volatile boolean anyDisabled;

    /** 全部开关 key(按元数据表顺序)。 */
    public static final String[] ALL_KEYS =
            AeroSuiteFeatures.ALL.stream().map(AeroSuiteFeatures.Feature::key).toArray(String[]::new);

    public static boolean isEnabled(String key) {
        AeroSuiteConfig c = CONFIG;
        if (c == null) return true; // 配置未加载前按全开处理(默认值全 true, 一致)
        var value = c.byKey.get(key);
        if (value == null) {
            AeroSuite.LOGGER.warn("Unknown feature gate key: {}", key);
            return true;
        }
        return value.get();
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
        for (AeroSuiteFeatures.Feature f : AeroSuiteFeatures.ALL) {
            if (isEnabled(f.key()))
                continue;
            for (var supplier : f.items())
                items.add(supplier.get());
        }
        return Set.copyOf(items);
    }

    private FeatureGates() {}
}
