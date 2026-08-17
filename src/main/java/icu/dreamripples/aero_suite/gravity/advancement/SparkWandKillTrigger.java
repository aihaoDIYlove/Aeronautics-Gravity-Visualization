package icu.dreamripples.aero_suite.gravity.advancement;

import com.mojang.serialization.Codec;
// import icu.dreamripples.aero_suite.gravity.item.SparkWandItem;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * 自定义成就触发器:玩家使用火花魔杖击杀节肢生物时触发。
 *
 * 触发点在 SparkWandItem.hurtEnemy 末尾 -- 此时普攻伤害(可能致死)与附加的 onFire 火伤
 * (可能致死)都已结算,只要 target.isDeadOrDying() 且属于 EntityTypeTags.ARTHROPOD 即调用 trigger。
 *
 * 不复用 vanilla player_killed_entity:火花魔杖的火伤走 damageSources().onFire(),
 * 该伤害源 causing entity 为 null,无法被 player_killed_entity 的 source_entity 谓词匹配;
 * 而节肢生物的火伤(8 点)远大于普攻(1 点),几乎全部由火伤完成击杀,纯数据驱动会失效。
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
