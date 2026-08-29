package icu.dreamripples.aero_suite.common.config;

import net.createmod.catnip.config.ConfigBase;
import net.createmod.catnip.config.ConfigBase.ConfigBool;
import net.createmod.catnip.config.ConfigBase.ConfigInt;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Aeronautics Suite 的 COMMON 配置树(挂在 gravity_visualization 名下, 三个 mod 共享一份)。
 * 结构不再手写字段, 由 {@link AeroSuiteFeatures#ALL} 元数据表驱动生成:
 * 每个 {@link AeroSuiteFeatures.Feature} 一个 {@link ConfigBool}, 按其 {@link AeroSuiteFeatures.Group}
 * 分入三个子树(gravity_visualization / simplification_related / starlight_logistics)。
 *
 * <p>配置页 UI 为自绘 {@code AeroSuiteConfigScreen}(Catnip BaseConfigScreen 不读 lang 文件,
 * 无法本地化, 已弃用), UI 的分页/顺序即本表顺序。
 *
 * <p>用 COMMON 而非 SERVER: 配方条件在(服务端)数据包加载时求值, COMMON 在服务器数据包
 * 加载前就绪, 时序安全; 且 COMMON 对所有存档全局生效, 符合"整套内容开关"的语义。
 */
public class AeroSuiteConfig extends ConfigBase {

    /** key -> ConfigBool(全部开关, 含三组)。 */
    public final Map<String, ConfigBool> byKey = new HashMap<>();

    /** 虚空之触数值(连锁上限/草丛半径/特殊方块耐久), 与开关树同文件存储。 */
    public final Tuning tuning;

    public AeroSuiteConfig() {
        Map<AeroSuiteFeatures.Group, FeatureGroup> groups = new EnumMap<>(AeroSuiteFeatures.Group.class);
        for (AeroSuiteFeatures.Feature f : AeroSuiteFeatures.ALL) {
            FeatureGroup group = groups.computeIfAbsent(f.group(), g ->
                    nested(0, () -> new FeatureGroup(g.id)));
            byKey.put(f.key(), group.add(f.key(), f.defaultValue()));
        }
        tuning = nested(0, Tuning::new);
    }

    @Override
    public String getName() {
        return "aero_suite";
    }

    /** 空子树: 全部 ConfigBool 经 {@link #add} 注册进 allValues。 */
    public static class FeatureGroup extends ConfigBase {
        private final String name;

        FeatureGroup(String name) { this.name = name; }

        @Override
        public String getName() { return name; }

        ConfigBool add(String key, boolean def) { return b(def, key); }
    }

    /** 虚空之触数值项: get() 在配置未注册前返回构造默认值。 */
    public static class Tuning extends ConfigBase {
        /** 普通方块连锁硬上限(实际 = min(玩家挡位, 此值); 挡位默认 16, 此处放开到最大挡 64)。 */
        public final ConfigInt chainLimit = i(64, 1, 256, "chainLimit");
        /** 草丛(矮草/高草)连锁上限。 */
        public final ConfigInt grassChainLimit = i(64, 1, 256, "grassChainLimit");
        /** 草丛连锁的跨空格连接半径(能隔几格连上), 普通方块恒为 1。 */
        public final ConfigInt grassReach = i(3, 1, 5, "grassReach");
        /** 末地传送门框架/传送门/折跃门一次采集的固定耐久消耗(无视 Unbreaking)。 */
        public final ConfigInt specialDurabilityCost = i(16, 1, 100, "specialDurabilityCost");

        @Override
        public String getName() { return "void_touch_tuning"; }
    }
}
