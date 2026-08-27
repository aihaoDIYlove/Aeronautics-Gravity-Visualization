package icu.dreamripples.aero_suite.starlight.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SuspendedTownParticle;
import net.minecraft.core.particles.SimpleParticleType;

import net.minecraft.util.RandomSource;

/**
 * 星光闪烁粒子: 继承 vanilla mycelium 粒子本体 SuspendedTownParticle(微小光点、原地悬浮、
 * 5~25 tick 随机寿命硬切出现/消失 = 星星闪烁感),仅重写配色为末地之海色系:
 * 青绿 50% / 长春花蓝 35% / 淡绿 15%,再叠亮度抖动还原色块深浅错落的星群观感。
 *
 * 渲染保持 PARTICLE_SHEET_OPAQUE(不透明硬切是闪烁感来源,勿改 translucent 淡入淡出)。
 * 贴图复用 vanilla generic_0(mycelium 同款白点),粒子 json 引用 minecraft:generic_0。
 */
public class StarlightSparkleParticle extends SuspendedTownParticle {

    public StarlightSparkleParticle(ClientLevel level, double x, double y, double z,
                                    double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        float[] col = new float[3];
        pickColor(level.random, col);
        setColor(col[0], col[1], col[2]);
    }

    // 末地之海配色(hex -> 0~1): #7FDCD4 / #8A93E8 / #A8E6B8,亮度乘 0.7~1.0 随机
    private static void pickColor(RandomSource random, float[] out) {
        float r, g, b;
        double roll = random.nextDouble();
        if (roll < 0.50) {          // 青绿(主)
            r = 0.50f; g = 0.86f; b = 0.83f;
        } else if (roll < 0.85) {   // 长春花蓝
            r = 0.54f; g = 0.58f; b = 0.91f;
        } else {                    // 淡绿(点缀)
            r = 0.66f; g = 0.90f; b = 0.72f;
        }
        float brightness = 0.7f + random.nextFloat() * 0.3f;
        out[0] = r * brightness; out[1] = g * brightness; out[2] = b * brightness;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public record Provider(SpriteSet sprite) implements ParticleProvider<SimpleParticleType> {
        @Override
        public StarlightSparkleParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                       double x, double y, double z,
                                                       double xSpeed, double ySpeed, double zSpeed) {
            StarlightSparkleParticle particle = new StarlightSparkleParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            particle.pickSprite(sprite);
            return particle;
        }
    }
}
