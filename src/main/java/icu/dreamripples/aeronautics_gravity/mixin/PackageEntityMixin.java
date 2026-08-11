package icu.dreamripples.aeronautics_gravity.mixin;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.item.ItemHelper;
import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import icu.dreamripples.aeronautics_gravity.item.ActivatedEnderPearlItem;
import icu.dreamripples.aeronautics_gravity.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 包裹静止 3 秒后破裂 -> 在原地生成一颗向下飞行的 {@link ThrownEnderpearl}(owner=激活珍珠
 * 记录的玩家 UUID), 落地由 vanilla {@code ThrownEnderpearl.onHit} 跨维度传送 owner 到落点.
 * <p>
 * 仅与包含 {@link ActivatedEnderPearlItem} 的包裹相关: 普通包裹不受影响(扫不到我们的物品 ->
 * 静止计数不累加 -> 不破裂).
 * <p>
 * 三种"非破裂"路径已被天然挡掉(见 plan 异常行为对照表):
 * <ul>
 *   <li>被目标物品栏接收 -> PackageEntity 被 Create 直接 {@code discard} 成 ItemStack, tick 停止.</li>
 *   <li>被玩家拾起 -> 同上, 直接 inventory 化.</li>
 *   <li>被火/爆炸/落水销毁 -> 走 {@code destroy}/{@code hurt} 路径, 不挂本 Mixin 的破裂钩子;
 *       语义: 销毁=受伤语境, 只算"安全抵达并静止落地"才传送(decision A).</li>
 * </ul>
 * <p>
 * <b>静止判定</b>: 用相邻 tick 的<t>位置差平方</t>而非 {@code deltaMovement}. 实测发现: Create
 * 包裹卡在传送带末端/溜槽死角时, {@code deltaMovement} 每 tick 被 Create 重新注入恒定 ~0.078 m/tick,
 * 但真实位置不变 -- 用 deltaMovement 会漏判这种"视觉静止"卡死状态. 改用位置差平方阈值 1.0E-6
 * (对应位置变化 <0.001 块/tick), 与传送带上 ~0.006 (≈0.078²) 真实位移差 4 个数量级, 可靠区分.
 * 3 秒计时(60 tick)避开任何短停顿(溜槽/弹射瞬停).
 * <p>
 * <b>就地生成珍珠</b>: 用 vanilla {@code new ThrownEnderpearl(Level, LivingEntity thrower)}
 * 构造(自动 setOwner=thrower), 再 {@code setPos} 覆写到包裹原位 + {@code shoot(0,-1,0,...)}
 * 向下飞一格立即落地. Vanilla onHit 内 {@code entity.changeDimension(DimensionTransition(...))}
 * 负责跨维度传送 owner, {@code connection.isAcceptingMessages()} 负责离线闸门. 离线玩家
 * -> 跳过传送, 珍珠碎裂消失, 等同"末影珍珠被销毁什么也不做".
 */
@Mixin(PackageEntity.class)
public abstract class PackageEntityMixin {

    /** 静止 60 game tick = 3 秒后破裂. */
    private static final int AG_RUPTURE_TICKS = 60;
    /** 静止判定: 相邻 tick 位置差平方阈值, < 1e-6 (≈0.001 块位移) 视为静止. 实测传送带上位置差≈0.006. */
    private static final double AG_STILL_POS_DELTA_SQR = 1.0E-6D;
    /** 诊断: 控制台每 N tick 才打一次状态, 避免日志爆炸. -1=关闭. */
    private static final int AG_DEBUG_INTERVAL = 20;
    private static final Logger AG_LOG = AeronauticsGravityVisualization.LOGGER;

    @Unique
    private int ag$stillTicks = 0;
    /** 上一 tick 的位置, 用于位置差判定. NaN 表示尚未初始化(首次 tick). */
    @Unique
    private double ag$lastX = Double.NaN;
    @Unique
    private double ag$lastY = Double.NaN;
    @Unique
    private double ag$lastZ = Double.NaN;

    /**
     * 影子 Create {@link PackageEntity#destroy(DamageSource)} 私有方法, 复用其破裂粒子包+
     * PACKAGE_POP 音效+内容物自然掉落(dropAllDeathLoot)的完整破裂语义, 不重新发明轮子.
     */
    @Shadow
    private void destroy(DamageSource source) {
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void aeronautics_gravity$ruptureTick(CallbackInfo ci) {
        PackageEntity self = (PackageEntity) (Object) this;
        if (!self.isAlive()) return;
        Level level = self.level();
        if (level.isClientSide) return;

        ItemStack box = self.getBox();
        if (!PackageItem.isPackage(box)) return;

        ItemStackHandler contents = PackageItem.getContents(box);
        int slot = aeronautics_gravity$findFirstActivatedPearl(contents);
        if (slot < 0) {
            // 普通包裹 -- 计数重置以免下一个含珍珠的包裹带入旧计数值, 不破裂
            ag$stillTicks = 0;
            return;
        }

        if (self.tickCount <= 20) {
            // 跳过实体刚 spawn 的位置 settle 阶段: 但仍同步 lastX/Y/Z, 让 settle 结束第一 tick
            // 拿到的是真实相邻 tick 差值, 而非"包裹 spawn 起始位置 vs settle 后位置"假位移
            ag$lastX = self.getX();
            ag$lastY = self.getY();
            ag$lastZ = self.getZ();
            return;
        }

        // 首次进入: 仅初始化 lastX/Y/Z, 这一 tick 不累加(否则会算出 0 位置差假静止)
        if (Double.isNaN(ag$lastX)) {
            ag$lastX = self.getX();
            ag$lastY = self.getY();
            ag$lastZ = self.getZ();
            ag$stillTicks = 0;
            return;
        }

        double dx = self.getX() - ag$lastX;
        double dy = self.getY() - ag$lastY;
        double dz = self.getZ() - ag$lastZ;
        double posDeltaSqr = dx * dx + dy * dy + dz * dz;
        ag$lastX = self.getX();
        ag$lastY = self.getY();
        ag$lastZ = self.getZ();

        // === [诊断] ===
        if (AG_DEBUG_INTERVAL > 0 && self.tickCount % AG_DEBUG_INTERVAL == 0) {
            AG_LOG.info("[AG-PackageRupture] tick={} onGround={} deltaSqr={} posDeltaSqr={} stillTicks={} slot={}",
                    self.tickCount, self.onGround(),
                    self.getDeltaMovement().lengthSqr(),
                    posDeltaSqr, ag$stillTicks, slot);
        }

        // 静止判定: 位置差平方 < 阈值. 传送带上 posDeltaSqr≈0.006, 卡死/落地静止≈0
        boolean still = posDeltaSqr < AG_STILL_POS_DELTA_SQR;
        if (!still) {
            ag$stillTicks = 0;
            return;
        }
        if (++ag$stillTicks < AG_RUPTURE_TICKS) return;

        // ========= 触发破裂 =========
        ag$stillTicks = 0;
        ItemStack pearlStack = contents.getStackInSlot(slot);
        UUID owner = ActivatedEnderPearlItem.getOwner(pearlStack);
        if (owner == null) return; // 非法激活珍珠(无 UUID), 放弃

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);

        // 先把激活珍珠格从 PACKAGE_CONTENTS 里清掉并写回, 避免 destroy -> dropAllDeathLoot
        // 把它当作 ItemEntity 再丢出造成"两颗珍珠"
        contents.setStackInSlot(slot, ItemStack.EMPTY);
        box.set(AllDataComponents.PACKAGE_CONTENTS, ItemHelper.containerContentsFromHandler(contents));

        if (player != null) {
            // 在线 -> 在包裹原位生成"向下飞行"的末影珍珠实体. thrower 构造会自动 setOwner(player),
            // 落地 vanilla onHit 完成跨维度传送+5 血伤害+音效粒子
            ThrownEnderpearl pearl = new ThrownEnderpearl(level, player);
            pearl.setPos(self.getX(), self.getY() + 0.5D, self.getZ());
            pearl.shoot(0.0D, -1.0D, 0.0D, 0.5F, 1.0F);
            level.addFreshEntity(pearl);
        }
        // 离线 -> 不生成珍珠(避免浪费), 包裹直接破裂, 剩余内容物(已剔除激活珍珠)正常掉出

        // 复用 Create 私有 destroy: 发 PackageDestroyPacket 破裂粒子 + PACKAGE_POP 音效 + 内容物 drop
        destroy(level.damageSources().generic());
        self.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
    }

    @Unique
    private static int aeronautics_gravity$findFirstActivatedPearl(ItemStackHandler contents) {
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack s = contents.getStackInSlot(i);
            if (s.is(ModItems.ACTIVATED_ENDER_PEARL.get())) {
                return i;
            }
        }
        return -1;
    }
}