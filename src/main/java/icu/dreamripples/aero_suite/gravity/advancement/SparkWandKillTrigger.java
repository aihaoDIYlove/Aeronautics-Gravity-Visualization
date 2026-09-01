package icu.dreamripples.aero_suite.gravity.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * 通用"恒真"自定义成就触发器(matches 恒真),被多个成就按各自 trigger ID 复用。
 *
 * 全部击杀/触发条件判定都在 Java 触发点完成(见 ModTriggers 各条目注释),
 * 这里不做任何过滤 -- JSON 只负责引用 trigger ID 与挂 display/parent。
 */
public class SparkWandKillTrigger extends SimpleCriterionTrigger<SparkWandKillTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, TriggerInstance::matches);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player)
                ).apply(instance, TriggerInstance::new)
        );

        public boolean matches() {
            // 节肢 + 致死判定已在 SparkWandItem.hurtEnemy 调用 trigger 前完成,这里恒真。
            return true;
        }
    }
}
