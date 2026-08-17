package icu.dreamripples.aero_suite.common.config;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * 配方启停条件: 配方 JSON 里
 * {@code "neoforge:conditions": [{"type": "gravity_visualization:feature_enabled", "key": "world_anchor"}]}
 * 条件为 false 时配方直接不存在(JEI 同步消失)。
 *
 * <p>{@code key} 是 {@link FeatureGates} 的开关名(与配置树叶子一一对应)。
 * 注册到 {@code NeoForgeRegistries.CONDITION_CODECS} 的 ns 是 gravity_visualization
 * (配置文件挂该 mod 名下, 条件类型跟随)。
 */
public record FeatureEnabledCondition(String key) implements ICondition {

    public static final MapCodec<FeatureEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    com.mojang.serialization.Codec.STRING.fieldOf("key").forGetter(FeatureEnabledCondition::key)
            ).apply(instance, FeatureEnabledCondition::new));

    @Override
    public boolean test(IContext context) {
        return FeatureGates.isEnabled(key);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "feature_enabled(\"" + key + "\")";
    }
}
