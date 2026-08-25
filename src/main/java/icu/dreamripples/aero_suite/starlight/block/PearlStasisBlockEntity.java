package icu.dreamripples.aero_suite.starlight.block;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 珍珠滞留台 BE: 单槽物品存放(恒 1 个, 不论物品自身堆叠上限), 无 GUI --
 * 右键存取, 漏斗/机械手经 capability 插入/抽取。物品渲染在方块内部
 * (见 {@code PearlStasisRenderer})。
 *
 * <p><b>红石传送</b>(顶面弹板三档模式, {@link TriggerMode}):
 * <ul>
 *   <li>{@code PULSE}(默认): 红石上升沿触发一次({@link #updateRedstone});</li>
 *   <li>{@code CONTINUOUS_ON}: 有红石时持续触发(tick 轮询, 装入即传);</li>
 *   <li>{@code CONTINUOUS_OFF}: 无红石时持续触发。</li>
 * </ul>
 *
 * <p><b>客户端同步</b>: 走 SmartBlockEntity 的 update packet(自带 behaviour 的
 * "ScrollValue" 键, tag 恒非空); 自定义字段经 {@link #write}/{@link #read}
 * (SmartBlockEntity 子类惯例, 覆写 loadAdditional 会绕过 behaviour 读写)。
 */
public class PearlStasisBlockEntity extends SmartBlockEntity {
    private static final Set<Item> PROJECTILE_ITEMS = Set.of(
            Items.SNOWBALL, Items.EGG, Items.FIRE_CHARGE, Items.WIND_CHARGE);

    private ItemStack held = ItemStack.EMPTY;
    /** 上一次已知的供电状态, 用于 PULSE 模式上升沿检测(瞬态, 不落盘)。 */
    private boolean wasPowered = false;
    /** 顶面弹板: 红石触发模式。 */
    private ScrollOptionBehaviour<TriggerMode> triggerModeBehaviour;

    public PearlStasisBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        triggerModeBehaviour = new ScrollOptionBehaviour<>(
                TriggerMode.class,
                Component.translatable("tooltip.starlight_logistics.pearl_stasis.mode"),
                this, new TopOnlyValueBoxTransform());
        triggerModeBehaviour.value = TriggerMode.PULSE.ordinal();
        behaviours.add(triggerModeBehaviour);
        // 单 behaviour: TYPE/netId/"ScrollValue" NBT key 均无共存冲突, 不需要 Stabilizer 那套三重隔离
    }

    public ItemStack getHeld() {
        return held;
    }

    /** 写入槽位(调用方保证恒 count=1), 标记 dirty 并同步客户端。 */
    public void setHeld(ItemStack stack) {
        this.held = stack;
        notifyUpdate();
    }

    public boolean isEmpty() {
        return held.isEmpty();
    }

    // ── 红石触发 ──────────────────────────────────────────────

    /** 由 Block.neighborChanged 调用: PULSE 模式检测供电上升沿。 */
    public void updateRedstone() {
        if (level == null || level.isClientSide()) return;
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (powered && !wasPowered && triggerModeBehaviour.get() == TriggerMode.PULSE) {
            tryTrigger(level, worldPosition);
        }
        wasPowered = powered;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) return;

        // 持续模式: 条件满足时每 tick 尝试(珍珠消耗后天然停; 玩家离线早退无副作用)
        TriggerMode mode = triggerModeBehaviour.get();
        if (mode == TriggerMode.PULSE) return;
        int signal = level.getBestNeighborSignal(worldPosition);
        if (mode.isContinuouslyActive(signal)) {
            tryTrigger(level, worldPosition);
        }
    }

    /**
     * 统一触发入口: 投掷物优先, 否则尝试珍珠传送。
     */
    private void tryTrigger(Level level, BlockPos pos) {
        if (held.isEmpty()) return;

        // ── 投掷物: 雪球/鸡蛋/烈焰弹/风弹(物品单例自身即 ProjectileItem)──
        if (held.getItem() instanceof ProjectileItem projectileItem && PROJECTILE_ITEMS.contains(held.getItem())) {
            launchProjectile(level, pos, projectileItem);
        } else {
            tryTeleport(level, pos);
        }
    }

    private void launchProjectile(Level level, BlockPos pos, ProjectileItem item) {
        // 发射点: 台面上方一格中心
        Vec3 spawn = new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
        Direction down = Direction.DOWN;
        Position position = new Position() {
            @Override public double x() { return spawn.x; }
            @Override public double y() { return spawn.y - 0.3; } // 实体原点略低于碰撞箱顶, 贴近台面
            @Override public double z() { return spawn.z; }
        };

        Projectile projectile = item.asProjectile(level, position, held, down);
        item.shoot(projectile, down.getStepX(), down.getStepY(), down.getStepZ(), 1.1F, 0.0F);
        projectile.setOwner(null); // 无射击者: 不归因玩家, 鸡蛋/雪球不掉落战利品给特定人
        level.addFreshEntity(projectile);
        level.levelEvent(1002, pos, 0);

        held.shrink(1);
        notifyUpdate();
    }

    private void tryTeleport(Level level, BlockPos pos) {
        if (held.isEmpty() || level.getServer() == null) return;
        UUID ownerUuid = icu.dreamripples.aero_suite.starlight.item.ActivatedEnderPearlItem.getOwnerUuid(held);
        if (ownerUuid == null) return;

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerUuid);
        if (player == null) return; // 离线不触发(珍珠保留)

        // ── 载具安全(恶性 bug 修复)──────────────────────────────
        // 物理化载具上的 BE, 其 level/坐标是 Sable SubLevel 本地系的。直接
        // teleportTo 会把玩家传进未注册的幽灵维度/plot 错位坐标 -- 玩家卡死、
        // 存档保存时维度遍历挂起坏档(同 AE2 空间塔搬折跃门物理化的死法)。
        // 正确做法: 用载具位姿(logicalPose)把目标点变换回父维度的真实世界坐标。
        Vec3 target = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        Level targetLevel = level;
        SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel != null) {
            target = subLevel.logicalPose().transformPosition(target);
            targetLevel = subLevel.getLevel();
        }

        // 终极保险: 目标必须是已注册的真实 ServerLevel(幽灵维度拒传, 珍珠保留)
        if (!(targetLevel instanceof ServerLevel destLevel)
                || destLevel.getServer().getLevel(destLevel.dimension()) == null) {
            return;
        }

        // 离场音效在玩家原位置播
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (player.level() == destLevel) {
            player.teleportTo(target.x, target.y, target.z);
        } else {
            player.teleportTo(destLevel, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        }

        destLevel.playSound(null, target.x, target.y, target.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        held = ItemStack.EMPTY; // 消耗
        notifyUpdate();
    }

    // ── 序列化(SmartBlockEntity 子类惯例: 覆写 write/read) ────

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!held.isEmpty()) {
            tag.put("HeldItem", held.save(registries));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        held = ItemStack.EMPTY;
        if (tag.contains("HeldItem")) {
            Optional<ItemStack> parsed = ItemStack.parse(registries, tag.getCompound("HeldItem"));
            held = parsed.orElse(ItemStack.EMPTY);
        }
    }

    // ── capability: 漏斗/机械手双向 1 格 ──────────────────────

    private final IItemHandler itemHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return held;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !held.isEmpty()) return stack;
            ItemStack remainder = stack.copyWithCount(stack.getCount() - 1);
            if (!simulate) {
                setHeld(stack.copyWithCount(1));
            }
            return remainder;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || held.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            ItemStack extracted = held.copy();
            if (!simulate) {
                setHeld(ItemStack.EMPTY);
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return held.isEmpty();
        }
    };

    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    // ── 弹板: 仅顶面 ─────────────────────────────────────────

    /** 触发模式弹板 Transform - 只在上表面显示(物品渲染占内部, 其余面干净)。 */
    private static class TopOnlyValueBoxTransform extends ValueBoxTransform.Sided {
        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction == Direction.UP;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 15.5);
        }
    }

    /**
     * 红石触发模式 - 顶面弹板三档。
     * PULSE: 上升沿触发一次(默认); CONTINUOUS_ON: 有红石时持续; CONTINUOUS_OFF: 无红石时持续。
     * 图标复用 Create 的 I_REFRESH(脉冲)/I_ACTIVE(有红石)/I_PASSIVE(无红石)。
     */
    public enum TriggerMode implements INamedIconOptions {
        PULSE(AllIcons.I_REFRESH),
        CONTINUOUS_ON(AllIcons.I_ACTIVE),
        CONTINUOUS_OFF(AllIcons.I_PASSIVE);

        private final AllIcons icon;
        private final String translationKey;

        TriggerMode(AllIcons icon) {
            this.icon = icon;
            this.translationKey = "tooltip.starlight_logistics.pearl_stasis.mode." + name().toLowerCase();
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }

        /** 持续模式(PULSE 恒 false): 给定红石信号, 本模式当前是否处于"持续激活"状态。 */
        public boolean isContinuouslyActive(int signal) {
            return this == CONTINUOUS_ON ? signal > 0 : this == CONTINUOUS_OFF && signal == 0;
        }
    }
}
