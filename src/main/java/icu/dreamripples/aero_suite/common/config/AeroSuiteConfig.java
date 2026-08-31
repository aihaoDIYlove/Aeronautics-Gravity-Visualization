package icu.dreamripples.aero_suite.common.config;

import net.createmod.catnip.config.ConfigBase;
import net.createmod.catnip.config.ConfigBase.ConfigBool;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** key -> ConfigBool(全部开关, 含三组)。ConcurrentHashMap: 配置屏(客户端线程)写 / 服务端读 */
    public final Map<String, ConfigBool> byKey = new ConcurrentHashMap<>();

    /** 数值特性组(配置屏"数值特性相关"页编辑; 现为机械手抓取的三个消耗值, 读取方 ExtendoGrabServer) */
    public final Tunables tunables = nested(0, Tunables::new);

    public AeroSuiteConfig() {
        Map<AeroSuiteFeatures.Group, FeatureGroup> groups = new EnumMap<>(AeroSuiteFeatures.Group.class);
        for (AeroSuiteFeatures.Feature f : AeroSuiteFeatures.ALL) {
            FeatureGroup group = groups.computeIfAbsent(f.group(), g ->
                    nested(0, () -> new FeatureGroup(g.id)));
            byKey.put(f.key(), group.add(f.key(), f.defaultValue()));
        }
    }

    @Override
    public String getName() {
        return "aero_suite";
    }

    /**
     * 数值特性(区别于布尔开关的可调数值, 随改随存 toml、读取方实时 get 即时生效)。
     * 语义/范围与 {@code ExtendoGrabServer} 的注释一一对应。
     */
    public static class Tunables extends ConfigBase {

        public static final float HUNGER_DEFAULT = 1.0f;
        public static final int DURABILITY_DEFAULT = 10;
        public static final int AIR_DEFAULT = 15;

        /** 抓取时每秒消耗的饥饿值(点); 0 = 不消耗 */
        public final ConfigFloat extendoGrabHunger =
                f(HUNGER_DEFAULT, 0f, 20f, "extendo_grab_hunger_per_second");
        /** 抓取时每秒消耗的机械手耐久; 0 = 不消耗(铜背罐代付不受此值影响) */
        public final ConfigInt extendoGrabDurability =
                i(DURABILITY_DEFAULT, 0, 100, "extendo_grab_durability_per_second");
        /** 穿背罐时代扣的背罐空气/秒(基础罐 900 点, 默认 15 = 整罐 1 分钟) */
        public final ConfigInt extendoGrabAir =
                i(AIR_DEFAULT, 1, 900, "extendo_grab_air_per_second");

        @Override
        public String getName() {
            return "tunables";
        }
    }

    /** 空子树: 全部 ConfigBool 经 {@link #add} 注册进 allValues。 */
    public static class FeatureGroup extends ConfigBase {
        private final String name;

        FeatureGroup(String name) { this.name = name; }

        @Override
        public String getName() { return name; }

        ConfigBool add(String key, boolean def) { return b(def, key); }
    }
}
