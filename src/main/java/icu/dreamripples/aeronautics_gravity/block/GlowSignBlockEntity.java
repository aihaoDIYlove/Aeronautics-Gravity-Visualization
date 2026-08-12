package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
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
 * 发光透明墙牌 BE:继承 SignBlockEntity 以满足 Create {@code PackagerBlockEntity.getSign} 的
 * {@code instanceof SignBlockEntity} 检查(否则机器读不到地址)。
 *
 * 数据双层:
 * <ul>
 *   <li>{@code ClipboardContent}(存于 vanilla BlockEntity 的 components,自动同步):给 ClipboardScreen
 *       编辑 GUI 和 BER 歌词式显示用。地址列表 = 所有页所有 entry 扁平化,每行一个地址。</li>
 *   <li>{@code SignText} front 第 0 行:给 Create 机器 getSign 读取(4 行拼接 = 激活地址)。
 *       由 {@link #updateSignTextFromSelected} 在 selected 变化时同步。</li>
 * </ul>
 *
 * 同步要点:vanilla 1.21.1 {@code BlockEntity.saveCustomOnly} 已自动把 components 编码进 NBT "components" key
 * 并同步客户端,所以本 BE 只需手动存 {@code selected}。{@code SignBlockEntity.markUpdated} 不检查 isClientSide,
 * 故 {@code updateSignTextFromSelected} 仅在 server 端调用(客户端 frontText 由 vanilla 自动同步)。
 *
 * 锁蜡:{@link #isWaxed()} 恒 true,防玩家右键编辑 / DisplayLink 误改。但 {@code setText} 不检查 isWaxed,
 * 所以程序化写入激活地址不受影响。
 */
public class GlowSignBlockEntity extends SignBlockEntity {

    private int selected = 0;
    // transient:从 components 重算,不存 NBT
    private List<String> addresses = new ArrayList<>();

    public GlowSignBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
        recomputeAddresses();
        selected = clampSelected(selected);
        updateSignTextFromSelected();
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

    // 滚轮切换(server,由 GlowSignScrollPayload handler 调用)
    public void setSelected(int value) {
        int clamped = clampSelected(value);
        if (clamped == selected && !addresses.isEmpty()) return;
        selected = clamped;
        updateSignTextFromSelected();
    }

    public int getSelected() {
        return selected;
    }

    public int clampSelected(int value) {
        return Mth.clamp(value, 0, Math.max(0, addresses.size() - 1));
    }

    public List<String> getAddresses() {
        return addresses;
    }

    // 扁平化:ClipboardContent.pages -> List<String>(每行一个地址,去空白)
    private void recomputeAddresses() {
        addresses = new ArrayList<>();
        ClipboardContent content = components().get(AllDataComponents.CLIPBOARD_CONTENT);
        if (content == null) return;
        for (List<ClipboardEntry> page : content.pages()) {
            for (ClipboardEntry entry : page) {
                String text = entry.text.getString();
                if (!text.isBlank()) {
                    addresses.add(text.trim());
                }
            }
        }
    }

    // 激活地址写进 SignText front 第 0 行(server only!markUpdated 不检查 isClientSide)
    private void updateSignTextFromSelected() {
        if (level == null || level.isClientSide) return;
        String address = addresses.isEmpty() ? "" : addresses.get(selected);
        SignText newText = new SignText()
                .setColor(DyeColor.WHITE)
                .setHasGlowingText(true)
                .setMessage(0, Component.literal(address));
        setText(newText, true);
    }

    // NBT:只存 selected,components 由 vanilla 自动同步
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("GlowSelected", selected);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // components 已由 vanilla BlockEntity.load 自动解码(line 86-87)
        selected = tag.getInt("GlowSelected");
        recomputeAddresses();
        selected = clampSelected(selected);
        // 不调 updateSignTextFromSelected:frontText 由 vanilla 同步,BER 不用 frontText
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        // 首次放置(无 NBT)或加载后:recompute + clamp(幂等)
        recomputeAddresses();
        selected = clampSelected(selected);
    }
}
