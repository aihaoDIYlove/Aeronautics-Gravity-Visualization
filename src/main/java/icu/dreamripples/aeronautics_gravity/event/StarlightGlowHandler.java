package icu.dreamripples.aeronautics_gravity.event;

import dev.simulated_team.simulated.content.end_sea.EndSeaPhysics;
import dev.simulated_team.simulated.content.end_sea.EndSeaPhysicsData;
import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 星空液体发光: 接触星空液体的生物/玩家获得发光效果(光灵箭同款, 15 秒).
 * <p>
 * 覆盖两种"星空液体"形态:
 * <ul>
 *   <li><b>星空液体方块</b>(桶倒出的 source): 由
 *       {@link icu.dreamripples.aeronautics_gravity.block.StarlightLiquidBlock#entityInside}
 *       在实体浸入方块时调用 {@link #applyGlow}.</li>
 *   <li><b>末地之海 region 力场</b>(虚空无 FluidState, 是 Simulated 的物理力场): 由本类监听
 *       {@link EntityTickEvent.Pre} 检测实体位于海区(y &lt; startY)时施加. 判据与
 *       {@link StarlightBottleHandler} 一致.</li>
 * </ul>
 * <p>
 * 刷新策略: 现有发光效果剩余 &lt; {@link #REFRESH_THRESHOLD} tick(3 秒)时才重新施加 15 秒, 避免每 tick
 * 创建 {@link MobEffectInstance} 对象. {@code addEffect} 默认按 max 合并, 不会无限堆叠时长; 持续接触
 * 保持 15 秒, 离开后自然到期.
 */
@EventBusSubscriber(modid = AeronauticsGravityVisualization.MOD_ID)
public class StarlightGlowHandler {

    private static final int GLOW_DURATION = 15 * 20;    // 15 秒 = 300 tick
    private static final int REFRESH_THRESHOLD = 3 * 20; // 剩余 < 3 秒才刷新

    public static void applyGlow(LivingEntity living) {
        MobEffectInstance existing = living.getEffect(MobEffects.GLOWING);
        if (existing == null || existing.getDuration() < REFRESH_THRESHOLD) {
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_DURATION));
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) return;
        Level level = living.level();
        if (level.isClientSide) return;
        EndSeaPhysics physics = EndSeaPhysicsData.of(level);
        if (physics == null) return;
        if (living.getY() >= physics.startY()) return;
        applyGlow(living);
    }
}
