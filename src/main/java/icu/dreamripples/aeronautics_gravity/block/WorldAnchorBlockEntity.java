package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.item.SmartInventory;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import icu.dreamripples.aeronautics_gravity.logistics.WorldAnchorNetwork;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
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
 *           本地多告示牌(贴了 2+ 个非空告示牌) -> signConflict=true(白灯,不注册,不接受包裹)。
 *
 * **告示牌配置**:照搬 Packager 的 updateSignAddress/getSign(去 ComputerCraft)。6 面任一告示牌,4 行拼接。
 *   贴多个非空告示牌视为配置冲突(白灯),避免按朝向顺序隐式取一个造成困惑。
 *
 * **强加载**:onLoad 调 ServerLevel.setChunkForced(true) 强加载自身区块,接收端永远在线。
 *
 * **灯带状态机**(ANCHOR_INDICATOR 染色,BER 读 getLampColor):
 *   SEND 空闲=红 / SEND 卡住=黄 / RECEIVE 空闲=青 / RECEIVE 缓存满=绿 / RECEIVE 地址冲突=白。
 *   conflicted/failedLastExport 服务端算,NBT sync 到客户端。
 */
public class WorldAnchorBlockEntity extends PackagePortBlockEntity implements IHaveGoggleInformation {

    // 灯带颜色(ARGB)
    private static final int COLOR_SEND_IDLE    = 0xFFCD0000; // 红
    private static final int COLOR_SEND_STUCK   = 0xFFFFAA00; // 黄/橙
    private static final int COLOR_RECV_IDLE    = 0xFF00CDCD; // 青
    private static final int COLOR_RECV_BUFFERED= 0xFF00FF00; // 绿
    private static final int COLOR_CONFLICT     = 0xFFFFFFFF; // 白

    private AnchorModeBehaviour modeBehaviour;
    public String signBasedAddress = "";

    // 服务端算, sync 到客户端供 BER 染色
    private boolean conflicted;        // 跨维度:同 addressFilter 多接收端
    private boolean signConflict;      // 本地:贴了 2+ 个非空告示牌
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
        if (signConflict) {
            // 本地多告示牌冲突,不注册(发送端找不到此端,NO_RECEIVER,不接受包裹)
            deregisterFromNetwork();
            return;
        }
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

    /**
     * 告示牌配置地址(照搬 Packager, 去 ComputerCraft)。
     * 6 面扫非空告示牌:0 个 -> 空地址;1 个 -> 该地址;2+ 个 -> signConflict=true(白灯,不注册)。
     * 变化时 sendData() 同步给客户端 BER。
     */
    public void updateSignAddress() {
        String first = null;
        int count = 0;
        for (Direction side : Iterate.directions) {
            String address = getSign(side);
            if (address == null || address.isBlank()) continue;
            if (first == null) first = address;
            count++;
        }
        String newAddress = (first != null) ? first : "";
        boolean newConflict = count > 1;
        boolean changed = !Objects.equals(signBasedAddress, newAddress) || (signConflict != newConflict);
        signBasedAddress = newAddress;
        signConflict = newConflict;
        if (changed) sendData();
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
            // 载具(SubLevel)上的世界锚点:BlockEntity.level 是父 ServerLevel,worldPosition 是
            // SubLevel 内坐标(即父维度的 plot chunk 坐标)。对父维度强加载该 plot chunk 既无意义
            // (SubLevel 由 Sable 管理生命周期),又会导致退出存档时 ChunkMap.processUnloads 卡死。
            if (isInsideSubLevel(sl)) return;
            sl.setChunkForced(worldPosition.getX() >> 4, worldPosition.getZ() >> 4, true);
        }
    }

    /** 判断本 BE 是否位于物理载具(SubLevel)内 -- 此时 level 是父 ServerLevel,worldPosition 落在 plot chunk 上 */
    private boolean isInsideSubLevel(ServerLevel sl) {
        SubLevelContainer container = SubLevelContainer.getContainer(sl);
        return container != null && container.getPlot(new ChunkPos(worldPosition)) != null;
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
            if (isInsideSubLevel(sl)) return;
            sl.setChunkForced(worldPosition.getX() >> 4, worldPosition.getZ() >> 4, false);
        }
    }

    /** 灯带颜色(客户端 BER 调用) */
    public int getLampColor() {
        if (getMode() == AnchorMode.SEND) {
            return failedLastExport ? COLOR_SEND_STUCK : COLOR_SEND_IDLE;
        } else {
            if (signConflict || conflicted) return COLOR_CONFLICT; // 本地多告示牌 / 跨维度同 filter 冲突
            if (isBackedUp()) return COLOR_RECV_BUFFERED;
            return COLOR_RECV_IDLE;
        }
    }

    /**
     * 护目镜信息: 标题 / 模式 / 内容物(包裹地址或"空") / (RECEIVE) 地址或"未配置地址"。
     * 状态提示色对齐灯带: SEND 卡住=黄("存在待发送包裹") / RECEIVE 缓存满=绿("存在待导出包裹") /
     * RECEIVE 地址冲突=白("地址冲突", 跟在地址值下)。
     * 必须返回 true: GoggleOverlayRenderer 第 138 行 hasGoggleInfo && !goggleAddedInfo 会跳过显示。
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AnchorMode mode = getMode();

        CreateLang.builder()
                .add(Component.translatable("block.aeronautics_gravity.world_anchor")
                        .withStyle(ChatFormatting.WHITE))
                .forGoggles(tooltip);

        // 模式(SEND=红 / RECEIVE=青, 对齐灯带色)
        ChatFormatting modeColor = (mode == AnchorMode.SEND) ? ChatFormatting.RED : ChatFormatting.AQUA;
        CreateLang.builder()
                .add(Component.translatable("tooltip.aeronautics_gravity.world_anchor.mode")
                        .withStyle(ChatFormatting.GRAY))
                .add(Component.literal(": ")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .add(Component.translatable(mode.getTranslationKey())
                        .withStyle(modeColor))
                .forGoggles(tooltip, 1);

        // 内容物(1 格包裹; 显示包裹地址, 空槽显示"空")
        ItemStack box = inventory.getStackInSlot(0);
        CreateLang.builder()
                .add(Component.translatable("tooltip.aeronautics_gravity.world_anchor.contents")
                        .withStyle(ChatFormatting.GRAY))
                .add(Component.literal(": ")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);
        if (box.isEmpty()) {
            CreateLang.builder()
                    .add(Component.translatable("tooltip.aeronautics_gravity.world_anchor.empty")
                            .withStyle(ChatFormatting.GRAY))
                    .forGoggles(tooltip, 2);
        } else {
            CreateLang.builder()
                    .add(Component.literal(PackageItem.getAddress(box))
                            .withStyle(ChatFormatting.GOLD))
                    .forGoggles(tooltip, 2);
        }

        if (mode == AnchorMode.SEND) {
            // 卡住: 上次导出失败(无接收端/冲突/满), 黄色提示
            if (failedLastExport) {
                CreateLang.builder()
                        .add(Component.translatable("tooltip.aeronautics_gravity.world_anchor.pending_send")
                                .withStyle(ChatFormatting.YELLOW))
                        .forGoggles(tooltip, 1);
            }
        } else {
            // RECEIVE: 地址行(告示牌配置的 addressFilter)
            CreateLang.builder()
                    .add(Component.translatable("tooltip.aeronautics_gravity.world_anchor.address")
                            .withStyle(ChatFormatting.GRAY))
                    .add(Component.literal(": ")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip, 1);
            if (signBasedAddress.isBlank()) {
                CreateLang.builder()
                        .add(Component.translatable("tooltip.aeronautics_gravity.world_anchor.no_address")
                                .withStyle(ChatFormatting.GRAY))
                        .forGoggles(tooltip, 2);
            } else {
                CreateLang.builder()
                        .add(Component.literal(signBasedAddress)
                                .withStyle(ChatFormatting.GOLD))
                        .forGoggles(tooltip, 2);
            }
            // 地址冲突(本地多告示牌 / 跨维度同 filter), 白色, 跟在地址值下
            if (signConflict || conflicted) {
                CreateLang.builder()
                        .add(Component.translatable("tooltip.aeronautics_gravity.world_anchor.address_conflict")
                                .withStyle(ChatFormatting.WHITE))
                        .forGoggles(tooltip, 2);
            }
            // 缓存满(待导出), 绿色提示
            if (isBackedUp()) {
                CreateLang.builder()
                        .add(Component.translatable("tooltip.aeronautics_gravity.world_anchor.pending_export")
                                .withStyle(ChatFormatting.GREEN))
                        .forGoggles(tooltip, 1);
            }
        }

        return true;
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
        tag.putBoolean("SignConflict", signConflict);
        tag.putBoolean("FailedLastExport", failedLastExport);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        signBasedAddress = tag.getString("SignAddress");
        conflicted = tag.getBoolean("Conflicted");
        signConflict = tag.getBoolean("SignConflict");
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
