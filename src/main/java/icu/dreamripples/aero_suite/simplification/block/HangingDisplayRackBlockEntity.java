package icu.dreamripples.aero_suite.simplification.block;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * 悬挂展示架 BE: 单槽物品存放(恒 1 个, 不论物品自身堆叠上限), 无 GUI。
 * 右键存取逻辑在 {@link HangingDisplayRackBlock}; 物品由 BER 渲染在薄板下方。
 *
 * <p>刻意用 SmartBlockEntity 而非裸 BlockEntity: 白拿 {@code notifyUpdate()} 的
 * update packet 同步(存/取后客户端 BER 立即重渲), 与珍珠滞留台同款。
 * 无 behaviour 无 tick。
 */
public class HangingDisplayRackBlockEntity extends SmartBlockEntity {

    private ItemStack held = ItemStack.EMPTY;

    public HangingDisplayRackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 无弹板行为; 覆写仅为满足抽象方法
    }

    public ItemStack getHeld() {
        return held;
    }

    public boolean isEmpty() {
        return held.isEmpty();
    }

    /** 写入槽位(调用方保证恒 count=1), 标记 dirty 并同步客户端。 */
    public void setHeld(ItemStack stack) {
        this.held = stack;
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
}
