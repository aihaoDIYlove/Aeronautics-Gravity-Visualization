package icu.dreamripples.aero_suite.starlight;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * mod3 粒子类型注册。粒子本体(SuspendedTownParticle 子类)与 Provider 是 client-only,
 * 挂在 AeronauticsGravityClient 的 RegisterParticleProvidersEvent;此处只注册 common 侧
 * 的 ParticleType,方块 animateTick 在两端都可见此类型但仅客户端实际发射。
 */
public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> STARLIGHT_PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, StarlightLogistics.MOD_ID);

    // 星光闪烁: 仿 vanilla mycelium 粒子(SuspendedTownParticle),末地之海配色(青/长春花蓝/淡绿)
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STARLIGHT_SPARKLE =
            STARLIGHT_PARTICLES.register("starlight_sparkle", () -> new SimpleParticleType(false));
}
