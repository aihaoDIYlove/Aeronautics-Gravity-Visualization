package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.item.SmartInventory;
import icu.dreamripples.aeronautics_gravity.logistics.WorldAnchorNetwork;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.List;
import java.util.Objects;

/**
 * 世界锚点 BE - 跨维度物流端口。继承 PackagePortBlockEntity 复用包裹物品栏/序列化/isBackedUp。
 *
 * **模式**(ScrollOptionBehaviour,6 面弹板):SEND 发送 / RECEIVE 接收。
 * - SEND:漏斗/溜槽导入包裹 -> tick 里读 PackageItem.getAddress -> WorldAnchorNetwork.send 跨维度传输。
 *         失败(无接收端/冲突/满) -> failedLastExport=true(黄灯)。
 * - RECEIVE:告示牌配置 addressFilter -> 注册到 WorldAnchorNetwork -> 接收端插入物品栏(被动)。
 *           漏斗/溜槽导出(不自动吐)。地址冲突(同 filter 多接收端) -> conflicted=true(白灯)。
 *
 * **告示牌配置**:照搬 Packager 的 updateSignAddress/getSign(去 ComputerCraft)。6 面任一告示牌,4 行拼接。
 *
 * **强加载**:onLoad 调 ServerLevel.setChunkForced(true) 强加载自身区块,接收端永远在线。
 *
 * **灯带状态机**(ANCHOR_INDICATOR 染色,BER 读 getLampColor):
 *   SEND 空闲=红 / SEND 卡住=黄 / RECEIVE 空闲=青 / RECEIVE 缓存满=绿 / RECEIVE 地址冲突=白。
 *   conflicted/failedLastExport 服务端算,NBT sync 到客户端。
 */
public class WorldAnchorBlockEntity extends PackagePortBlockEntity {

    // 灯带颜色(ARGB)
    private static final int COLOR_SEND_IDLE    = 0xFFCD0000; // 红
    private static final int COLOR_SEND_STUCK   = 0xFFFFAA00; // 黄/橙
    private static final int COLOR_RECV_IDLE    = 0xFF00CDCD; // 青
    private static final int COLOR_RECV_BUFFERED= 0xFF00FF00; // 绿
    private static final int COLOR_CONFLICT     = 0xFFFFFFFF; // 白

    private AnchorModeBehaviour modeBehaviour;
    public String signBasedAddress = "";

    // 服务端算, sync 到客户端供 BER 染色
    private boolean conflicted;
    private boolean failedLastExport;

    // 网络注册跟踪(避免重复注册/注销)
    private boolean registered;
    private String registeredAddress;

    public WorldAnchorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        // 覆盖父类 18 格 inventory 为 1 格(仅接受包裹)
        inventory = new SmartInventory(1, this, (slot, stack) -> PackageItem.isPackage(stack));
        // 直通 inventory 作为 handler,不用 PackagePortAutomationInventoryWrapper:后者的 insert/extract
        // 绑定蛙港 addressFilter 语义(提取要求包裹匹配 filter,插入拒绝匹配包裹),世界锚点用告示牌地址
        // 体系,addressFilter 恒空 -> 提取永远被拒(漏斗抽不出,实测 bug)。SmartInventory 自身已做 isPackage 验证。
        itemHandler = inventory;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours); // openTracker (世界锚点不开 UI,保留无害)
        modeBehaviour = new AnchorModeBehaviour(
                AnchorMode.class,
                Component.translatable("tooltip.aeronautics_gravity.world_anchor.mode"),
                this,
                new AnchorModeValueBoxTransform());
        modeBehaviour.value = 0; // 默认 SEND
        behaviours.add(modeBehaviour);
    }

    @Override
    protected void onOpenChange(boolean open) {
        // 世界锚点不开放 UI, 空实现
    }

    public AnchorMode getMode() {
        return modeBehaviour.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        AnchorMode mode = getMode();
        if (mode == AnchorMode.SEND) {
            deregisterFromNetwork();
            ItemStack box = inventory.getStackInSlot(0);
            if (!box.isEmpty()) {
                String addr = PackageItem.getAddress(box);
                WorldAnchorNetwork.SendResult result = WorldAnchorNetwork.send(
                        Objects.requireNonNull(level.getServer()), addr, box.copy());
                if (result == WorldAnchorNetwork.SendResult.SUCCESS) {
                    inventory.setStackInSlot(0, ItemStack.EMPTY);
                    notifyUpdate();
                    if (failedLastExport) { failedLastExport = false; sendData(); }
                } else {
                    if (!failedLastExport) { failedLastExport = true; sendData(); }
                }
            } else if (failedLastExport) {
                // 物品已被漏斗/玩家移除,卡住状态无意义,清除
                failedLastExport = false;
                sendData();
            }
        } else {
            registerToNetwork();
            boolean newConflicted = WorldAnchorNetwork.isConflicted(signBasedAddress);
            if (newConflicted != conflicted) {
                conflicted = newConflicted;
                sendData();
            }
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide) return;
        updateSignAddress();
    }

    private void registerToNetwork() {
        if (registered && Objects.equals(registeredAddress, signBasedAddress)) return;
        deregisterFromNetwork();
        if (!signBasedAddress.isBlank()) {
            WorldAnchorNetwork.register(signBasedAddress, level.dimension(), worldPosition);
            registered = true;
            registeredAddress = signBasedAddress;
        }
    }

    private void deregisterFromNetwork() {
        if (registered && registeredAddress != null) {
            WorldAnchorNetwork.deregister(registeredAddress, level.dimension(), worldPosition);
        }
        registered = false;
        registeredAddress = null;
    }

    /** 告示牌配置地址(照搬 Packager, 去 ComputerCraft) */
    public void updateSignAddress() {
        signBasedAddress = "";
        for (Direction side : Iterate.directions) {
            String address = getSign(side);
            if (address == null || address.isBlank()) continue;
            signBasedAddress = address;
        }
    }

    protected String getSign(Direction side) {
        BlockEntity be = level.getBlockEntity(worldPosition.relative(side));
        if (!(be instanceof SignBlockEntity sign)) return null;
        for (boolean front : Iterate.trueAndFalse) {
            SignText text = sign.getText(front);
            String address = "";
            for (Component component : text.getMessages(false)) {
                String string = component.getString();
                if (!string.isBlank()) address += string.trim() + " ";
            }
            if (!address.isBlank()) return address.trim();
        }
        return null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl && !level.isClientSide) {
            sl.setChunkForced(worldPosition.getX() >> 4, worldPosition.getZ() >> 4, true);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        deregisterFromNetwork();
    }

    @Override
    public void destroy() {
        super.destroy();
        if (level instanceof ServerLevel sl && !level.isClientSide) {
            sl.setChunkForced(worldPosition.getX() >> 4, worldPosition.getZ() >> 4, false);
        }
    }

    /** 灯带颜色(客户端 BER 调用) */
    public int getLampColor() {
        if (getMode() == AnchorMode.SEND) {
            return failedLastExport ? COLOR_SEND_STUCK : COLOR_SEND_IDLE;
        } else {
            if (conflicted) return COLOR_CONFLICT;
            if (isBackedUp()) return COLOR_RECV_BUFFERED;
            return COLOR_RECV_IDLE;
        }
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlocks.WORLD_ANCHOR_BE.get(),
                (be, context) -> be.itemHandler);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("SignAddress", signBasedAddress);
        tag.putBoolean("Conflicted", conflicted);
        tag.putBoolean("FailedLastExport", failedLastExport);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        signBasedAddress = tag.getString("SignAddress");
        conflicted = tag.getBoolean("Conflicted");
        failedLastExport = tag.getBoolean("FailedLastExport");
    }

    /** 模式 ScrollOptionBehaviour - 独立 BehaviourType + 独立 NBT key */
    private static class AnchorModeBehaviour extends ScrollOptionBehaviour<AnchorMode> {
        public static final BehaviourType<AnchorModeBehaviour> TYPE = new BehaviourType<>();

        public AnchorModeBehaviour(Class<AnchorMode> enumClass, Component label,
                                   WorldAnchorBlockEntity be, ValueBoxTransform slot) {
            super(enumClass, label, be, slot);
        }

        @Override
        public BehaviourType<?> getType() { return TYPE; }

        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("AnchorMode", value);
        }

        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            value = nbt.getInt("AnchorMode");
        }
    }

    /** 世界锚点模式 - 6 面弹板切换。SEND 发送(红)/RECEIVE 接收(青)。图标复用 Create 的 I_PASSIVE/I_ACTIVE。 */
    public enum AnchorMode implements INamedIconOptions {
        SEND(AllIcons.I_PASSIVE),
        RECEIVE(AllIcons.I_ACTIVE);

        private final AllIcons icon;
        private final String translationKey;

        AnchorMode(AllIcons icon) {
            this.icon = icon;
            this.translationKey = "tooltip.aeronautics_gravity.world_anchor.mode." + name().toLowerCase();
        }

        @Override
        public AllIcons getIcon() { return icon; }

        @Override
        public String getTranslationKey() { return translationKey; }
    }

    /** 6 面都显示弹板 */
    private static class AnchorModeValueBoxTransform extends ValueBoxTransform.Sided {
        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return true;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 15.5);
        }
    }
}
