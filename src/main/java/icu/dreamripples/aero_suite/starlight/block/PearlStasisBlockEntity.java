package icu.dreamripples.aero_suite.starlight.block;

import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * 珍珠滞留台 BE: 单槽物品存放(恒 1 个, 不论物品自身堆叠上限), 无 GUI --
 * 右键存取, 漏斗/机械手经 capability 插入/抽取。物品渲染在方块内部 4px 高处
 * (见 {@code PearlStasisRenderer})。
 *
 * <p><b>红石传送</b>: 红石上升沿({@link #updateRedstone})时, 若槽内是
 * <b>已绑定玩家</b>的激活末影珍珠({@code ActivatedEnderPearlItem.getOwner} 非空),
 * 把该玩家传送到滞留台上方一格(跨维度直接 {@code ServerPlayer.teleportTo}),
 * 并消耗该珍珠。未绑定珍珠/其他物品/玩家离线均不触发。
 *
 * <p><b>客户端同步</b>: 无 menu, 走 {@link #getUpdatePacket}/{@link #getUpdateTag}
 * 全量 NBT 同步(仅一个 item 字段, 开销可忽略), 变更时
 * {@code level.sendBlockUpdated} 触发重发。
 */
public class PearlStasisBlockEntity extends BlockEntity {

    private ItemStack held = ItemStack.EMPTY;
    /** 上一次已知的供电状态, 用于上升沿检测(瞬态, 不落盘)。 */
    private boolean wasPowered = false;

    public PearlStasisBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.PEARL_STASIS_BE.get(), pos, state);
    }

    public ItemStack getHeld() {
        return held;
    }

    /** 写入槽位(调用方保证恒 count=1), 标记 dirty 并同步客户端。 */
    public void setHeld(ItemStack stack) {
        this.held = stack;
        setChangedAndSync();
    }

    public boolean isEmpty() {
        return held.isEmpty();
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    // ── 红石上升沿 ────────────────────────────────────────────

    /** 由 Block.neighborChanged 调用: 检测供电上升沿, 触发珍珠传送。 */
    public void updateRedstone(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered && !wasPowered) {
            tryTeleport(level, pos);
        }
        wasPowered = powered;
    }

    private void tryTeleport(Level level, BlockPos pos) {
        if (held.isEmpty() || level.getServer() == null) return;
        UUID ownerUuid = icu.dreamripples.aero_suite.starlight.item.ActivatedEnderPearlItem.getOwnerUuid(held);
        if (ownerUuid == null) return;

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerUuid);
        if (player == null) return; // 离线不触发(珍珠保留, 上线后再来一次上升沿)

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;

        // 离场音效在玩家原位置播
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (player.level() == level) {
            player.teleportTo(x, y, z);
        } else {
            player.teleportTo((ServerLevel) level, x, y, z, player.getYRot(), player.getXRot());
        }

        level.playSound(null, x, y, z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        held = ItemStack.EMPTY; // 消耗
        setChangedAndSync();
    }

    // ── 序列化 + 客户端同步 ───────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!held.isEmpty()) {
            tag.put("HeldItem", held.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        held = ItemStack.EMPTY;
        if (tag.contains("HeldItem")) {
            Optional<ItemStack> parsed = ItemStack.parse(registries, tag.getCompound("HeldItem"));
            held = parsed.orElse(ItemStack.EMPTY);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        // NeoForge onDataPacket 对空 tag 直接忽略(IBlockEntityExtension: if (!tag.isEmpty())),
        // 取走物品后 saveAdditional 恰好写不出任何键 -> 客户端永不收到"已清空"。
        // 恒写一个键保证 tag 非空, 客户端才会走 loadAdditional 清空 held。
        tag.putBoolean("PearlStasisSync", true);
        return tag;
    }

    @Override
    public Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
}
