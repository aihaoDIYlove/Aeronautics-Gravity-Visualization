package icu.dreamripples.aero_suite.block;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 寻址牌 BE:继承 SignBlockEntity 以满足 Create {@code PackagerBlockEntity.getSign} 的
 * {@code instanceof SignBlockEntity} 检查(否则机器读不到地址)。
 *
 * 数据双层:
 * <ul>
 *   <li>{@code ClipboardContent}(存于 vanilla BlockEntity 的 components):给 ClipboardScreen
 *       编辑 GUI 和 BER 歌词式显示用。地址列表 = 所有页所有 entry 扁平化,每行一个地址。
 *       由 {@link #getAddresses()} 从 components 实时算(不缓存),保证客户端 components 同步后立即生效。</li>
 *   <li>{@code SignText} front 第 0 行:给 Create 机器 getSign 读取(4 行拼接 = 激活地址)。
 *       由 {@link #updateSignTextFromSelected} 在 selected/components 变化时同步(server only)。</li>
 * </ul>
 *
 * 同步要点(踩坑修正):
 * <ul>
 *   <li>vanilla {@code SignBlockEntity.getUpdateTag} 调 {@code saveCustomOnly},后者 <b>不编码 components</b>
 *       (只有 {@code saveWithoutMetadata} 编码)。故 update packet 默认不含 ClipboardContent,
 *       客户端 components 恒空 -> 编辑后"不保存"。本 BE 覆写 {@link #getUpdateTag} 手动编码 components。</li>
 *   <li>客户端 {@code loadWithComponents} 先调 {@code loadAdditional} 再设 components,故
 *       {@code loadAdditional} 内不能 recompute(读到旧 components)。{@code getAddresses} 改为实时算,
 *       规避时序问题。</li>
 *   <li>{@code SignBlockEntity.markUpdated} 不检查 isClientSide,故 {@code updateSignTextFromSelected}
 *       仅在 server 端调用(客户端 frontText 由 vanilla 自动同步)。</li>
 * </ul>
 *
 * 锁蜡:{@link #isWaxed()} 恒 true,防玩家右键编辑 / DisplayLink 误改。但 {@code setText} 不检查 isWaxed,
 * 所以程序化写入激活地址不受影响。
 */
public class AddressingSignBlockEntity extends SignBlockEntity {

    private int selected = 0;

    public AddressingSignBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean isWaxed() {
        return true;
    }

    // ClipboardEditPacket mixin 调用(server 端)
    @Override
    public void setComponents(DataComponentMap components) {
        super.setComponents(components);
        if (level != null && !level.isClientSide) {
            selected = clampSelected(selected);
            updateSignTextFromSelected();
        }
    }

    /**
     * ClipboardEditPacket mixin 写入 components 后调用(等价 ClipboardBlockEntity.onEditedBy)。
     * setComponents 已做重算,这里只额外确保同步。
     */
    public void onClipboardEdited(Player player) {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    // 滚轮切换(server,由 AddressingSignScrollPayload handler 调用)
    public void setSelected(int value) {
        int clamped = clampSelected(value);
        if (clamped == selected && !getAddresses().isEmpty()) return;
        selected = clamped;
        if (level != null && !level.isClientSide) {
            updateSignTextFromSelected();
        }
    }

    public int getSelected() {
        return selected;
    }

    public int clampSelected(int value) {
        int size = getAddresses().size();
        return Mth.clamp(value, 0, Math.max(0, size - 1));
    }

    // 从 components 实时算(不缓存):客户端 components 由 vanilla loadWithComponents 同步后立即生效,
    // 无需 recompute 回调(loadAdditional 在 components 更新前执行,缓存会读到旧值)
    public List<String> getAddresses() {
        List<String> result = new ArrayList<>();
        ClipboardContent content = components().get(AllDataComponents.CLIPBOARD_CONTENT);
        if (content == null) return result;
        for (List<ClipboardEntry> page : content.pages()) {
            for (ClipboardEntry entry : page) {
                String text = entry.text.getString();
                if (!text.isBlank()) {
                    result.add(text.trim());
                }
            }
        }
        return result;
    }

    // 激活地址写进 SignText front 第 0 行(server only!markUpdated 不检查 isClientSide)
    private void updateSignTextFromSelected() {
        if (level == null || level.isClientSide) return;
        List<String> addrs = getAddresses();
        String address;
        if (addrs.isEmpty()) {
            address = "";
        } else {
            int idx = Mth.clamp(selected, 0, addrs.size() - 1);
            address = addrs.get(idx);
        }
        SignText newText = new SignText()
                .setColor(DyeColor.WHITE)
                .setHasGlowingText(true)
                .setMessage(0, Component.literal(address));
        setText(newText, true);
    }

    // 关键修复:SignBlockEntity.getUpdateTag -> saveCustomOnly,不编码 components!
    // 不覆写则 ClipboardContent 永远不同步客户端(编辑后客户端 components 仍空,告示牌显示空列表提示)
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        DataComponentMap.CODEC.encodeStart(
                registries.createSerializationContext(NbtOps.INSTANCE), components())
            .result()
            .ifPresent(encoded -> tag.put("components", encoded));
        return tag;
    }

    // NBT:只存 selected,components 由 vanilla saveWithoutMetadata 自动编码(存盘)
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("AddressingSelected", selected);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // components 由 vanilla loadWithComponents 在本方法之后解码,故此处不 recompute
        selected = tag.getInt("AddressingSelected");
    }
}
