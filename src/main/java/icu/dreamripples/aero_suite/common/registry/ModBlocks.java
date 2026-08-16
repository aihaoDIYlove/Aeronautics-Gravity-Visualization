package icu.dreamripples.aero_suite.common.registry;

import com.simibubi.create.content.decoration.encasing.CasingBlock;
import icu.dreamripples.aero_suite.common.registry.ModCapabilities;
import icu.dreamripples.aero_suite.common.registry.ModItems;
import icu.dreamripples.aero_suite.gravity.block.CounterweightBlock;
import icu.dreamripples.aero_suite.gravity.block.CounterweightBlockEntity;
import icu.dreamripples.aero_suite.gravity.block.CounterweightLightBlock;
import icu.dreamripples.aero_suite.gravity.block.CounterweightLightBlockEntity;
import icu.dreamripples.aero_suite.gravity.block.RedstoneCounterweightBlock;
import icu.dreamripples.aero_suite.gravity.block.RedstoneCounterweightBlockEntity;
import icu.dreamripples.aero_suite.gravity.block.RedstoneCounterweightLightBlock;
import icu.dreamripples.aero_suite.gravity.block.RedstoneCounterweightLightBlockEntity;
import icu.dreamripples.aero_suite.simplification.block.ConvenientAnalogTransmissionBlock;
import icu.dreamripples.aero_suite.simplification.block.ConvenientAnalogTransmissionBlockEntity;
import icu.dreamripples.aero_suite.simplification.block.SequentialFeederBlock;
import icu.dreamripples.aero_suite.simplification.block.SequentialFeederBlockEntity;
import icu.dreamripples.aero_suite.simplification.block.VariableSpeedPortableEngineBlock;
import icu.dreamripples.aero_suite.simplification.block.VariableSpeedPortableEngineBlockEntity;
import icu.dreamripples.aero_suite.starlight.block.AddressingSignBlock;
import icu.dreamripples.aero_suite.starlight.block.AddressingSignBlockEntity;
import icu.dreamripples.aero_suite.starlight.block.LightweightGlassBlock;
import icu.dreamripples.aero_suite.starlight.block.StabilizerBlock;
import icu.dreamripples.aero_suite.starlight.block.StabilizerBlockEntity;
import icu.dreamripples.aero_suite.starlight.block.StarlightEncasedPipeBlock;
import icu.dreamripples.aero_suite.starlight.block.StarlightLiquidBlock;
import icu.dreamripples.aero_suite.starlight.block.UltralightGlassBlock;
import icu.dreamripples.aero_suite.starlight.block.VoidHosePulleyBlock;
import icu.dreamripples.aero_suite.starlight.block.VoidHosePulleyBlockEntity;
import icu.dreamripples.aero_suite.starlight.block.VoidHosePulleyFluidHandler;
import icu.dreamripples.aero_suite.starlight.block.WorldAnchorBlock;
import icu.dreamripples.aero_suite.starlight.block.WorldAnchorBlockEntity;
import icu.dreamripples.aero_suite.starlight.event.StarlightGlowHandler;
import icu.dreamripples.aero_suite.starlight.fluid.ModFluids;
import icu.dreamripples.aero_suite.starlight.fluid.StarlightFluid;
import icu.dreamripples.aero_suite.starlight.item.AddressingSignBlockItem;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import icu.dreamripples.aero_suite.common.AeroSuiteIds;
import icu.dreamripples.aero_suite.starlight.StarlightLogistics;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public class ModBlocks {
    public static final DeferredRegister<Block> GRAVITY_BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AeroSuiteIds.GRAVITY_ID);
    public static final DeferredRegister<BlockEntityType<?>> GRAVITY_BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeroSuiteIds.GRAVITY_ID);

    public static final DeferredRegister<Block> SIMPLIFICATION_BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AeroSuiteIds.SIMPLIFICATION_ID);
    public static final DeferredRegister<BlockEntityType<?>> SIMPLIFICATION_BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeroSuiteIds.SIMPLIFICATION_ID);

    public static final DeferredRegister<Block> STARLIGHT_BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AeroSuiteIds.STARLIGHT_ID);
    public static final DeferredRegister<BlockEntityType<?>> STARLIGHT_BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeroSuiteIds.STARLIGHT_ID);

    public static final DeferredHolder<Block, ConvenientAnalogTransmissionBlock> CONVENIENT_ANALOG_TRANSMISSION_BLOCK =
            SIMPLIFICATION_BLOCKS.register("convenient_analog_transmission",
                    () -> new ConvenientAnalogTransmissionBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConvenientAnalogTransmissionBlockEntity>> CONVENIENT_ANALOG_TRANSMISSION_BE =
            SIMPLIFICATION_BLOCK_ENTITIES.register("convenient_analog_transmission",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createBlockEntity,
                                    CONVENIENT_ANALOG_TRANSMISSION_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<Item, BlockItem> CONVENIENT_ANALOG_TRANSMISSION_ITEM =
            ModItems.SIMPLIFICATION_ITEMS.register("convenient_analog_transmission",
                    () -> new BlockItem(CONVENIENT_ANALOG_TRANSMISSION_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CounterweightBlock> COUNTERWEIGHT_BLOCK =
            GRAVITY_BLOCKS.register("counterweight",
                    () -> new CounterweightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CounterweightBlockEntity>> COUNTERWEIGHT_BE =
            GRAVITY_BLOCK_ENTITIES.register("counterweight",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createCounterweightBlockEntity,
                                    COUNTERWEIGHT_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_ITEM =
            ModItems.GRAVITY_ITEMS.register("counterweight",
                    () -> new BlockItem(COUNTERWEIGHT_BLOCK.get(), new Item.Properties()));

    // 红石配重块:共用 RedstoneCounterweightBlock(MASS_TIER 1..16),红石信号驱动。
    // 轻量 BE(无 tick 无 NBT)仅为挂载灯带染色 Flywheel visual。
    public static final DeferredHolder<Block, RedstoneCounterweightBlock> COUNTERWEIGHT_REDSTONE_BLOCK =
            GRAVITY_BLOCKS.register("counterweight_redstone",
                    () -> new RedstoneCounterweightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_REDSTONE_ITEM =
            ModItems.GRAVITY_ITEMS.register("counterweight_redstone",
                    () -> new BlockItem(COUNTERWEIGHT_REDSTONE_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CounterweightLightBlock> COUNTERWEIGHT_LIGHT_BLOCK =
            GRAVITY_BLOCKS.register("counterweight_light",
                    () -> new CounterweightLightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, CounterweightLightBlock> COUNTERWEIGHT_LIGHT_PEARL_BLOCK =
            GRAVITY_BLOCKS.register("counterweight_light_pearl",
                    () -> new CounterweightLightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    // 珠光配"轻"块只是换皮,物理与普通配"轻"块完全一致,共用一个 BE 类型
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CounterweightLightBlockEntity>> COUNTERWEIGHT_LIGHT_BE =
            GRAVITY_BLOCK_ENTITIES.register("counterweight_light",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createCounterweightLightBlockEntity,
                                    COUNTERWEIGHT_LIGHT_BLOCK.get(),
                                    COUNTERWEIGHT_LIGHT_PEARL_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_LIGHT_ITEM =
            ModItems.GRAVITY_ITEMS.register("counterweight_light",
                    () -> new BlockItem(COUNTERWEIGHT_LIGHT_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_LIGHT_PEARL_ITEM =
            ModItems.GRAVITY_ITEMS.register("counterweight_light_pearl",
                    () -> new BlockItem(COUNTERWEIGHT_LIGHT_PEARL_BLOCK.get(), new Item.Properties()));

    // 红石配"轻"块系列:普通/珠光两种皮,共用 RedstoneCounterweightLightBlock(LIFT_TIER 1..16),红stone信号驱动。
    // 轻量 BE(无 tick 无 NBT)仅为挂载灯带染色 Flywheel visual。
    public static final DeferredHolder<Block, RedstoneCounterweightLightBlock> COUNTERWEIGHT_LIGHT_REDSTONE_BLOCK =
            GRAVITY_BLOCKS.register("counterweight_light_redstone",
                    () -> new RedstoneCounterweightLightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, RedstoneCounterweightLightBlock> COUNTERWEIGHT_LIGHT_PEARL_REDSTONE_BLOCK =
            GRAVITY_BLOCKS.register("counterweight_light_pearl_redstone",
                    () -> new RedstoneCounterweightLightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_LIGHT_REDSTONE_ITEM =
            ModItems.GRAVITY_ITEMS.register("counterweight_light_redstone",
                    () -> new BlockItem(COUNTERWEIGHT_LIGHT_REDSTONE_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_LIGHT_PEARL_REDSTONE_ITEM =
            ModItems.GRAVITY_ITEMS.register("counterweight_light_pearl_redstone",
                    () -> new BlockItem(COUNTERWEIGHT_LIGHT_PEARL_REDSTONE_BLOCK.get(), new Item.Properties()));

    // 轻质玻璃: 继承 Create 的 ConnectedGlassBlock, 复用 skipRendering(相邻同类不画内面,视觉上连成一片)。
    // 质量 0.25(Create/原版玻璃默认 1), 用于给物理载具减重。单帧贴图, 不做 CT 连接纹理。
    public static final DeferredHolder<Block, LightweightGlassBlock> LIGHTWEIGHT_GLASS_BLOCK =
            STARLIGHT_BLOCKS.register("lightweight_glass",
                    () -> new LightweightGlassBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.NONE)
                                    .sound(SoundType.GLASS)
                                    .strength(0.3f)
                                    .noOcclusion()
                                    .isValidSpawn((state, level, pos, entity) -> false)
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .isSuffocating((state, level, pos) -> false)
                                    .isViewBlocking((state, level, pos) -> false)));

    public static final DeferredHolder<Item, BlockItem> LIGHTWEIGHT_GLASS_ITEM =
            ModItems.STARLIGHT_ITEMS.register("lightweight_glass",
                    () -> new BlockItem(LIGHTWEIGHT_GLASS_BLOCK.get(), new Item.Properties()));

    // 超轻玻璃: 32x32 贴图(边框更细), 质量 0.125, 易碎(sable:fragile)。CT 连接纹理同轻质玻璃。
    public static final DeferredHolder<Block, UltralightGlassBlock> ULTRALIGHT_GLASS_BLOCK =
            STARLIGHT_BLOCKS.register("ultralight_glass",
                    () -> new UltralightGlassBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.NONE)
                                    .sound(SoundType.GLASS)
                                    .strength(0.0f)
                                    .noOcclusion()
                                    .isValidSpawn((state, level, pos, entity) -> false)
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .isSuffocating((state, level, pos) -> false)
                                    .isViewBlocking((state, level, pos) -> false)));

    public static final DeferredHolder<Item, BlockItem> ULTRALIGHT_GLASS_ITEM =
            ModItems.STARLIGHT_ITEMS.register("ultralight_glass",
                    () -> new BlockItem(ULTRALIGHT_GLASS_BLOCK.get(), new Item.Properties()));

    // 星空液体方块: source 流体不蔓延(见 StarlightFluid.tick), 贴图为自制 starlight_still/flow (tools/gen_starlight.py).
    // 用 StarlightLiquidBlock 子类: 实体浸入时获得发光效果(光灵箭同款, 15秒, 见 StarlightGlowHandler).
    // 不注册 BlockItem -- 流体方块用桶拾取, 不作为物品.
    public static final DeferredHolder<Block, StarlightLiquidBlock> STARLIGHT_BLOCK =
            STARLIGHT_BLOCKS.register("starlight", () -> new StarlightLiquidBlock(
                    ModFluids.STARLIGHT.get(),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noLootTable()
                            .lightLevel(state -> 7)));  // 流体方块投射光亮度 7(岩浆 15/火把 14; 此处走 BlockBehaviour.Properties, FluidType.lightLevel 只让桶贴图发光不投射)

    // 自稳定方块:PD 混合 + 倾斜速度自适应增益调度,互斥调节 MASS_TIER(1..16)/LIFT_TIER(1..16)维持载具水平。
    // 红石仅使能(signal=0 停用);右键 ScrollValueBehaviour 调死区 0..30°(默认 3°)。详见 StabilizerBlockEntity Javadoc。
    public static final DeferredHolder<Block, StabilizerBlock> STABILIZER_BLOCK =
            STARLIGHT_BLOCKS.register("stabilizer",
                    () -> new StabilizerBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .requiresCorrectToolForDrops()
                                    .emissiveRendering((s, level, pos) -> true)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StabilizerBlockEntity>> STABILIZER_BE =
            STARLIGHT_BLOCK_ENTITIES.register("stabilizer",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createStabilizerBlockEntity, STABILIZER_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<Item, BlockItem> STABILIZER_ITEM =
            ModItems.STARLIGHT_ITEMS.register("stabilizer",
                    () -> new BlockItem(STABILIZER_BLOCK.get(), new Item.Properties()));

    // 世界锚点:跨维度物流核心。继承 PackagePortBlockEntity,6 面弹板切发送/接收(ScrollOptionBehaviour),
    // 告示牌配地址(照搬 Packager),强加载自身区块。灯带 ANCHOR_INDICATOR(32 内嵌框住星空) + portal 星空。
    // 详见 WorldAnchorBlockEntity Javadoc。
    public static final DeferredHolder<Block, WorldAnchorBlock> WORLD_ANCHOR_BLOCK =
            STARLIGHT_BLOCKS.register("world_anchor",
                    () -> new WorldAnchorBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .requiresCorrectToolForDrops()
                                    .emissiveRendering((s, level, pos) -> true)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorldAnchorBlockEntity>> WORLD_ANCHOR_BE =
            STARLIGHT_BLOCK_ENTITIES.register("world_anchor",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createWorldAnchorBlockEntity, WORLD_ANCHOR_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<Item, BlockItem> WORLD_ANCHOR_ITEM =
            ModItems.STARLIGHT_ITEMS.register("world_anchor",
                    () -> new BlockItem(WORLD_ANCHOR_BLOCK.get(), new Item.Properties()));

    // 虚空软管滑轮: 继承 Create HosePulleyBlock, BE 换成 VoidHosePulleyBlockEntity(自带无限星空液体 handler)。
    // 外观/动画复用 Create 的 hose_pulley model + HosePulleyRenderer。在末地之海区域(末端 y<=startY)时
    // 经 capability 暴露的 VoidHosePulleyFluidHandler.drain 返回无限 STARLIGHT(见 ModCapabilities)。
    public static final DeferredHolder<Block, VoidHosePulleyBlock> VOID_HOSE_PULLEY_BLOCK =
            STARLIGHT_BLOCKS.register("void_hose_pulley",
                    () -> new VoidHosePulleyBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()
                                    .emissiveRendering((s, level, pos) -> true)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoidHosePulleyBlockEntity>> VOID_HOSE_PULLEY_BE =
            STARLIGHT_BLOCK_ENTITIES.register("void_hose_pulley",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createVoidHosePulleyBlockEntity, VOID_HOSE_PULLEY_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<Item, BlockItem> VOID_HOSE_PULLEY_ITEM =
            ModItems.STARLIGHT_ITEMS.register("void_hose_pulley",
                    () -> new BlockItem(VOID_HOSE_PULLEY_BLOCK.get(), new Item.Properties()));

    // 星空机壳: 继承 Create CasingBlock(铜机壳改色), 有 CT 连接纹理 + 可套管道(见 StarlightEncasedPipeBlock).
    // 合成: 手持星空液体瓶右键铜机壳 / 机械手 / 注液器 250mb starlight 注液. 详见 Feature 9.
    public static final DeferredHolder<Block, CasingBlock> STARLIGHT_CASING_BLOCK =
            STARLIGHT_BLOCKS.register("starlight_casing",
                    () -> new CasingBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()
                                    .emissiveRendering((s, level, pos) -> true)));

    public static final DeferredHolder<Item, BlockItem> STARLIGHT_CASING_ITEM =
            ModItems.STARLIGHT_ITEMS.register("starlight_casing",
                    () -> new BlockItem(STARLIGHT_CASING_BLOCK.get(), new Item.Properties()));

    // 星空套壳管道: 继承 EncasedPipeBlock(见 StarlightEncasedPipeBlock), 覆盖 getBlockEntityType 指向自注册 BE.
    // starlight_casing 右键 fluid_pipe -> 此方块; 扳手拆除还原 fluid_pipe(继承 onWrenched). 无 BlockItem(不可直接放置).
    public static final DeferredHolder<Block, StarlightEncasedPipeBlock> STARLIGHT_ENCASED_FLUID_PIPE_BLOCK =
            STARLIGHT_BLOCKS.register("starlight_encased_fluid_pipe",
                    () -> new StarlightEncasedPipeBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops(),
                            STARLIGHT_CASING_BLOCK::get));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidPipeBlockEntity>> STARLIGHT_ENCASED_FLUID_PIPE_BE =
            STARLIGHT_BLOCK_ENTITIES.register("starlight_encased_fluid_pipe",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createStarlightEncasedFluidPipeBlockEntity,
                                    STARLIGHT_ENCASED_FLUID_PIPE_BLOCK.get())
                            .build(null));

    // 变速式便携引擎:继承 Simulated 便携引擎,弹板调转速(32-256,15档),超热只翻倍应力。
    // 16 色变种(同原版 DyedBlockList 配色),染色交互指向本 mod 色表(见 VariableSpeedPortableEngineBlock.useItemOn)。
    // 创造页只放红色(同 Aeronautics 风格),其余靠染色获得。
    public static final Map<DyeColor, DeferredHolder<Block, VariableSpeedPortableEngineBlock>> VARIABLE_SPEED_PORTABLE_ENGINES =
            new EnumMap<>(DyeColor.class);
    public static final Map<DyeColor, DeferredHolder<Item, BlockItem>> VARIABLE_SPEED_PORTABLE_ENGINE_ITEMS =
            new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor color : DyeColor.values()) {
            String name = color.getSerializedName() + "_variable_speed_portable_engine";
            DeferredHolder<Block, VariableSpeedPortableEngineBlock> blockHolder = SIMPLIFICATION_BLOCKS.register(name,
                    () -> new VariableSpeedPortableEngineBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.NETHERITE_BLOCK)
                                    .lightLevel(state -> PortableEngineBlock.isLitState(state) ? 6 : 0)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops(), color));
            VARIABLE_SPEED_PORTABLE_ENGINES.put(color, blockHolder);
            VARIABLE_SPEED_PORTABLE_ENGINE_ITEMS.put(color,
                    ModItems.SIMPLIFICATION_ITEMS.register(name, () -> new BlockItem(blockHolder.get(), new Item.Properties())));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VariableSpeedPortableEngineBlockEntity>> VARIABLE_SPEED_PORTABLE_ENGINE_BE =
            SIMPLIFICATION_BLOCK_ENTITIES.register("variable_speed_portable_engine", () -> {
                Block[] blocks = VARIABLE_SPEED_PORTABLE_ENGINES.values().stream()
                        .map(h -> h.get())
                        .toArray(Block[]::new);
                return BlockEntityType.Builder.of(ModBlocks::createVariableSpeedEngineBlockEntity, blocks).build(null);
            });

    // 寻址牌:继承 WallSignBlock,无物理碰撞(可穿过)+ 选取箱保留。纯透明贴图(BER 不画木牌),
    // 文字默认发光+白色。shift+右键打开 Create ClipboardScreen 编辑地址列表(数据存 ClipboardContent),
    // shift+滚轮切换选中地址(歌词式 4 行显示)。激活地址同步写 SignText front 第 0 行供机器 getSign 读取。
    public static final DeferredHolder<Block, AddressingSignBlock> ADDRESSING_SIGN_BLOCK =
            STARLIGHT_BLOCKS.register("addressing_sign", () -> new AddressingSignBlock(
                    StarlightLogistics.ADDRESSING_SIGN_WOOD_TYPE,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.NONE)
                            .sound(SoundType.GLASS)
                            .strength(0.3f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false)
                            .isSuffocating((s, l, p) -> false)
                            .isViewBlocking((s, l, p) -> false)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AddressingSignBlockEntity>> ADDRESSING_SIGN_BE =
            STARLIGHT_BLOCK_ENTITIES.register("addressing_sign", () -> BlockEntityType.Builder
                    .of(ModBlocks::createAddressingSignBlockEntity, ADDRESSING_SIGN_BLOCK.get())
                    .build(null));

    public static final DeferredHolder<Item, BlockItem> ADDRESSING_SIGN_ITEM =
            ModItems.STARLIGHT_ITEMS.register("addressing_sign",
                    () -> new AddressingSignBlockItem(ADDRESSING_SIGN_BLOCK.get(), new Item.Properties()));

    private static ConvenientAnalogTransmissionBlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ConvenientAnalogTransmissionBlockEntity(CONVENIENT_ANALOG_TRANSMISSION_BE.get(), pos, state);
    }

    private static CounterweightBlockEntity createCounterweightBlockEntity(BlockPos pos, BlockState state) {
        return new CounterweightBlockEntity(COUNTERWEIGHT_BE.get(), pos, state);
    }

    private static CounterweightLightBlockEntity createCounterweightLightBlockEntity(BlockPos pos, BlockState state) {
        return new CounterweightLightBlockEntity(COUNTERWEIGHT_LIGHT_BE.get(), pos, state);
    }

    // 红石配重/配轻块的轻量 BE - 仅为挂 Flywheel visual(灯带染色),无 tick 无 NBT
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RedstoneCounterweightBlockEntity>> REDSTONE_COUNTERWEIGHT_BE =
            GRAVITY_BLOCK_ENTITIES.register("redstone_counterweight",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createRedstoneCounterweightBlockEntity,
                                    COUNTERWEIGHT_REDSTONE_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RedstoneCounterweightLightBlockEntity>> REDSTONE_COUNTERWEIGHT_LIGHT_BE =
            GRAVITY_BLOCK_ENTITIES.register("redstone_counterweight_light",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createRedstoneCounterweightLightBlockEntity,
                                    COUNTERWEIGHT_LIGHT_REDSTONE_BLOCK.get(),
                                    COUNTERWEIGHT_LIGHT_PEARL_REDSTONE_BLOCK.get())
                            .build(null));

    private static RedstoneCounterweightBlockEntity createRedstoneCounterweightBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneCounterweightBlockEntity(REDSTONE_COUNTERWEIGHT_BE.get(), pos, state);
    }

    private static RedstoneCounterweightLightBlockEntity createRedstoneCounterweightLightBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneCounterweightLightBlockEntity(REDSTONE_COUNTERWEIGHT_LIGHT_BE.get(), pos, state);
    }

    private static StabilizerBlockEntity createStabilizerBlockEntity(BlockPos pos, BlockState state) {
        return new StabilizerBlockEntity(STABILIZER_BE.get(), pos, state);
    }

    private static WorldAnchorBlockEntity createWorldAnchorBlockEntity(BlockPos pos, BlockState state) {
        return new WorldAnchorBlockEntity(WORLD_ANCHOR_BE.get(), pos, state);
    }

    private static VoidHosePulleyBlockEntity createVoidHosePulleyBlockEntity(BlockPos pos, BlockState state) {
        return new VoidHosePulleyBlockEntity(VOID_HOSE_PULLEY_BE.get(), pos, state);
    }

    // FluidPipeBlockEntity 构造是 3 参数(type,pos,state), 而 BlockEntityType.Builder.of 需要
    // 2 参数 BlockEntitySupplier(pos,state), 不能直接 method ref -> 工厂方法补上 BE type.
    private static FluidPipeBlockEntity createStarlightEncasedFluidPipeBlockEntity(BlockPos pos, BlockState state) {
        return new FluidPipeBlockEntity(STARLIGHT_ENCASED_FLUID_PIPE_BE.get(), pos, state);
    }

    private static VariableSpeedPortableEngineBlockEntity createVariableSpeedEngineBlockEntity(BlockPos pos, BlockState state) {
        return new VariableSpeedPortableEngineBlockEntity(VARIABLE_SPEED_PORTABLE_ENGINE_BE.get(), pos, state);
    }

    // 顺序供料器:可编程投料磁带。9 标记槽(幽灵)+ 9 物品槽,红石上升沿在"本步已输出"
    // 时前进指针(自节拍),外部机械手/漏斗每次最多取当前步 1 个。详见 SequentialFeederBlockEntity。
    public static final DeferredHolder<Block, SequentialFeederBlock> SEQUENTIAL_FEEDER_BLOCK =
            SIMPLIFICATION_BLOCKS.register("sequential_feeder",
                    () -> new SequentialFeederBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SequentialFeederBlockEntity>> SEQUENTIAL_FEEDER_BE =
            SIMPLIFICATION_BLOCK_ENTITIES.register("sequential_feeder",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createSequentialFeederBlockEntity,
                                    SEQUENTIAL_FEEDER_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<Item, BlockItem> SEQUENTIAL_FEEDER_ITEM =
            ModItems.SIMPLIFICATION_ITEMS.register("sequential_feeder",
                    () -> new BlockItem(SEQUENTIAL_FEEDER_BLOCK.get(), new Item.Properties()));

    private static SequentialFeederBlockEntity createSequentialFeederBlockEntity(BlockPos pos, BlockState state) {
        return new SequentialFeederBlockEntity(SEQUENTIAL_FEEDER_BE.get(), pos, state);
    }

    private static AddressingSignBlockEntity createAddressingSignBlockEntity(BlockPos pos, BlockState state) {
        return new AddressingSignBlockEntity(ADDRESSING_SIGN_BE.get(), pos, state);
    }
}
