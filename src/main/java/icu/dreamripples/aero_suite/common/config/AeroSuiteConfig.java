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

    /** 空子树: 全部 ConfigBool 经 {@link #add} 注册进 allValues。 */
    public static class FeatureGroup extends ConfigBase {
        private final String name;

        FeatureGroup(String name) { this.name = name; }

        @Override
        public String getName() { return name; }

        ConfigBool add(String key, boolean def) { return b(def, key); }
    }
}
