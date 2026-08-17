package icu.dreamripples.aero_suite.mixin.starlight;

import com.simibubi.create.AllDataComponents;
import icu.dreamripples.aero_suite.common.registry.ModItems;
import icu.dreamripples.aero_suite.gravity.advancement.ModTriggers;
import icu.dreamripples.aero_suite.starlight.item.ActivatedEnderPearlItem;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.item.ItemHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 含激活末影珍珠的包裹实体存在 3 秒(60 tick)后破裂 -> 在原地生成一颗向下飞行的
 * {@link ThrownEnderpearl}(owner=激活珍珠记录的玩家 UUID), 落地由 vanilla
 * {@code ThrownEnderpearl.onHit} 跨维度传送 owner 到落点.
 * <p>
 * <b>为什么用"实体存在时长"而非"静止判定"</b>: Create 传动系统(传送带=TransportedItemStack,
 * 锁链=ChainConveyorPackage)上的包裹是 {@link ItemStack} 而非 PackageEntity 实体, 本 Mixin
 * 无法触及. PackageEntity 只在包裹脱离传动系统(掉落/弹射/玩家丢弃)后存在, 而 Create 的
 * {@code insertionDelay} 机制保证包裹被传动系统接收时 PackageEntity 被 discard. 所以
 * "实体存在 3 秒"≈"脱离传动系统 3 秒未被接收" -> 触发跨维度传送. 飞行中的包裹也算(用户决策).
 * <p>
 * 三种"非破裂"路径天然挡掉:
 * <ul>
 *   <li>被目标物品栏接收 -> PackageEntity 被 Create {@code discard} 成 ItemStack, tick 停止.</li>
 *   <li>被玩家拾起 -> 同上, 直接 inventory 化.</li>
 *   <li>被火/爆炸/落水销毁 -> 走 {@code destroy}/{@code hurt} 路径, 不挂本 Mixin 的破裂钩子.</li>
 * </ul>
 * <p>
 * <b>区块加载</b>: 含珍珠期间每 100 tick 加一张 {@link TicketType#PORTAL} 票(300 tick 过期,
 * distance 3), 保持自身区块加载确保计时持续. 模仿 vanilla {@code Entity.placePortalTicket}.
 * 破裂后不再续票, 300 tick 内自动释放, 无泄漏.
 * <p>
 * <b>就地生成珍珠</b>: 用 vanilla {@code new ThrownEnderpearl(Level, LivingEntity thrower)}
 * 构造(自动 setOwner=thrower), 再 {@code setPos} 覆写到包裹原位 + {@code shoot(0,-1,0,...)}
 * 向下飞一格立即落地. Vanilla onHit 内 {@code entity.changeDimension(DimensionTransition(...))}
 * 负责跨维度传送 owner, {@code connection.isAcceptingMessages()} 负责离线闸门. 离线玩家
 * -> 跳过传送, 珍珠碎裂消失, 等同"末影珍珠被销毁什么也不做".
 */
@Mixin(PackageEntity.class)
public abstract class PackageEntityMixin {

    /** 实体存在 60 game tick = 3 秒后破裂. */
    private static final int AG_RUPTURE_TICKS = 60;

    /** 上次加 PORTAL 票的 tickCount, -1 表示从未加过(首次立即加). */
    @Unique
    private int ag$lastTicketTick = -1;

    /**
     * 影子 Create {@link PackageEntity#destroy(DamageSource)} 私有方法, 复用其破裂粒子包+
     * PACKAGE_POP 音效+内容物自然掉落(dropAllDeathLoot)的完整破裂语义, 不重新发明轮子.
     */
    @Shadow
    private void destroy(DamageSource source) {
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void aero_suite$ruptureTick(CallbackInfo ci) {
        PackageEntity self = (PackageEntity) (Object) this;
        if (!self.isAlive()) return;
        Level level = self.level();
        if (level.isClientSide) return;

        ItemStack box = self.getBox();
        if (!PackageItem.isPackage(box)) return;

        ItemStackHandler contents = PackageItem.getContents(box);
        int slot = aero_suite$findFirstActivatedPearl(contents);
        if (slot < 0) return; // 普通包裹, 不处理

        // === 含激活珍珠: 保持自身区块加载, 确保计时能持续到破裂 ===
        // PORTAL 票 300 tick 过期, 每 100 tick 续一张; 首次(lastTicketTick == -1)立即加.
        // 模仿 vanilla Entity.placePortalTicket. 不用 setChunkForced(持久化, 移动实体泄漏 +
        // SubLevel 卡死坑, 见 WorldAnchorBlockEntity). 破裂后不再续票, 300 tick 内自动释放.
        if (level instanceof ServerLevel sl) {
            if (ag$lastTicketTick < 0 || self.tickCount - ag$lastTicketTick >= 100) {
                sl.getChunkSource().addRegionTicket(
                        TicketType.PORTAL,
                        self.chunkPosition(),
                        3,
                        self.blockPosition());
                ag$lastTicketTick = self.tickCount;
            }
        }

        // === 实体存在 3 秒(60 tick)后破裂 ===
        // PackageEntity 只在脱离传动系统(掉落/弹射/玩家丢弃)后存在; Create 的 insertionDelay 机制
        // 保证包裹被传动系统接收时 PackageEntity 被 discard. 所以"实体存在 3 秒"≈"脱离传动系统
        // 3 秒未被接收" -> 触发跨维度传送. 飞行中的包裹也算(用户决策: 不管飞行状态).
        if (self.tickCount < AG_RUPTURE_TICKS) return;

        // ========= 触发破裂 =========
        ItemStack pearlStack = contents.getStackInSlot(slot);
        UUID owner = ActivatedEnderPearlItem.getOwnerUuid(pearlStack);
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
            // 成就"打包自己": 含激活珍珠的包裹成功破裂并生成珍珠 -> 触发(玩家在线即视为成功传送)
            ModTriggers.PEARL_PACKAGE_TELEPORT.get().trigger(player);
        }
        // 离线 -> 不生成珍珠(避免浪费), 包裹直接破裂, 剩余内容物(已剔除激活珍珠)正常掉出

        // 复用 Create 私有 destroy: 发 PackageDestroyPacket 破裂粒子 + PACKAGE_POP 音效 + 内容物 drop
        destroy(level.damageSources().generic());
        self.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
    }

    @Unique
    private static int aero_suite$findFirstActivatedPearl(ItemStackHandler contents) {
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack s = contents.getStackInSlot(i);
            if (s.is(ModItems.ACTIVATED_ENDER_PEARL.get())) {
                return i;
            }
        }
        return -1;
    }
}