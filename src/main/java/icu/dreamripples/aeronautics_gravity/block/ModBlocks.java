package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import icu.dreamripples.aeronautics_gravity.fluid.ModFluids;
import icu.dreamripples.aeronautics_gravity.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AeronauticsGravityVisualization.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeronauticsGravityVisualization.MOD_ID);

    public static final DeferredHolder<Block, ConvenientAnalogTransmissionBlock> CONVENIENT_ANALOG_TRANSMISSION_BLOCK =
            BLOCKS.register("convenient_analog_transmission",
                    () -> new ConvenientAnalogTransmissionBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConvenientAnalogTransmissionBlockEntity>> CONVENIENT_ANALOG_TRANSMISSION_BE =
            BLOCK_ENTITIES.register("convenient_analog_transmission",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createBlockEntity,
                                    CONVENIENT_ANALOG_TRANSMISSION_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<Item, BlockItem> CONVENIENT_ANALOG_TRANSMISSION_ITEM =
            ModItems.ITEMS.register("convenient_analog_transmission",
                    () -> new BlockItem(CONVENIENT_ANALOG_TRANSMISSION_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CounterweightBlock> COUNTERWEIGHT_BLOCK =
            BLOCKS.register("counterweight",
                    () -> new CounterweightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CounterweightBlockEntity>> COUNTERWEIGHT_BE =
            BLOCK_ENTITIES.register("counterweight",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createCounterweightBlockEntity,
                                    COUNTERWEIGHT_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_ITEM =
            ModItems.ITEMS.register("counterweight",
                    () -> new BlockItem(COUNTERWEIGHT_BLOCK.get(), new Item.Properties()));

    // 红石配重块:共用 RedstoneCounterweightBlock(MASS_TIER 1..16),红石信号驱动。
    // 轻量 BE(无 tick 无 NBT)仅为挂载灯带染色 Flywheel visual。
    public static final DeferredHolder<Block, RedstoneCounterweightBlock> COUNTERWEIGHT_REDSTONE_BLOCK =
            BLOCKS.register("counterweight_redstone",
                    () -> new RedstoneCounterweightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_REDSTONE_ITEM =
            ModItems.ITEMS.register("counterweight_redstone",
                    () -> new BlockItem(COUNTERWEIGHT_REDSTONE_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Block, CounterweightLightBlock> COUNTERWEIGHT_LIGHT_BLOCK =
            BLOCKS.register("counterweight_light",
                    () -> new CounterweightLightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, CounterweightLightBlock> COUNTERWEIGHT_LIGHT_PEARL_BLOCK =
            BLOCKS.register("counterweight_light_pearl",
                    () -> new CounterweightLightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    // 珠光配"轻"块只是换皮,物理与普通配"轻"块完全一致,共用一个 BE 类型
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CounterweightLightBlockEntity>> COUNTERWEIGHT_LIGHT_BE =
            BLOCK_ENTITIES.register("counterweight_light",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createCounterweightLightBlockEntity,
                                    COUNTERWEIGHT_LIGHT_BLOCK.get(),
                                    COUNTERWEIGHT_LIGHT_PEARL_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_LIGHT_ITEM =
            ModItems.ITEMS.register("counterweight_light",
                    () -> new BlockItem(COUNTERWEIGHT_LIGHT_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_LIGHT_PEARL_ITEM =
            ModItems.ITEMS.register("counterweight_light_pearl",
                    () -> new BlockItem(COUNTERWEIGHT_LIGHT_PEARL_BLOCK.get(), new Item.Properties()));

    // 红石配"轻"块系列:普通/珠光两种皮,共用 RedstoneCounterweightLightBlock(LIFT_TIER 1..16),红stone信号驱动。
    // 轻量 BE(无 tick 无 NBT)仅为挂载灯带染色 Flywheel visual。
    public static final DeferredHolder<Block, RedstoneCounterweightLightBlock> COUNTERWEIGHT_LIGHT_REDSTONE_BLOCK =
            BLOCKS.register("counterweight_light_redstone",
                    () -> new RedstoneCounterweightLightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, RedstoneCounterweightLightBlock> COUNTERWEIGHT_LIGHT_PEARL_REDSTONE_BLOCK =
            BLOCKS.register("counterweight_light_pearl_redstone",
                    () -> new RedstoneCounterweightLightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_LIGHT_REDSTONE_ITEM =
            ModItems.ITEMS.register("counterweight_light_redstone",
                    () -> new BlockItem(COUNTERWEIGHT_LIGHT_REDSTONE_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_LIGHT_PEARL_REDSTONE_ITEM =
            ModItems.ITEMS.register("counterweight_light_pearl_redstone",
                    () -> new BlockItem(COUNTERWEIGHT_LIGHT_PEARL_REDSTONE_BLOCK.get(), new Item.Properties()));

    // 轻质玻璃: 继承 Create 的 ConnectedGlassBlock, 复用 skipRendering(相邻同类不画内面,视觉上连成一片)。
    // 质量 0.25(Create/原版玻璃默认 1), 用于给物理载具减重。单帧贴图, 不做 CT 连接纹理。
    public static final DeferredHolder<Block, LightweightGlassBlock> LIGHTWEIGHT_GLASS_BLOCK =
            BLOCKS.register("lightweight_glass",
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
            ModItems.ITEMS.register("lightweight_glass",
                    () -> new BlockItem(LIGHTWEIGHT_GLASS_BLOCK.get(), new Item.Properties()));

    // 超轻玻璃: 32x32 贴图(边框更细), 质量 0.125, 易碎(sable:fragile)。CT 连接纹理同轻质玻璃。
    public static final DeferredHolder<Block, UltralightGlassBlock> ULTRALIGHT_GLASS_BLOCK =
            BLOCKS.register("ultralight_glass",
                    () -> new UltralightGlassBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.NONE)
                                    .sound(SoundType.GLASS)
                                    .strength(0.3f)
                                    .noOcclusion()
                                    .isValidSpawn((state, level, pos, entity) -> false)
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .isSuffocating((state, level, pos) -> false)
                                    .isViewBlocking((state, level, pos) -> false)));

    public static final DeferredHolder<Item, BlockItem> ULTRALIGHT_GLASS_ITEM =
            ModItems.ITEMS.register("ultralight_glass",
                    () -> new BlockItem(ULTRALIGHT_GLASS_BLOCK.get(), new Item.Properties()));

    // 星空液体方块: source 流体不蔓延(见 StarlightFluid.tick), 贴图为自制 starlight_still/flow (tools/gen_starlight.py).
    // 不注册 BlockItem -- 流体方块用桶拾取, 不作为物品.
    public static final DeferredHolder<Block, LiquidBlock> STARLIGHT_BLOCK =
            BLOCKS.register("starlight", () -> new LiquidBlock(
                    ModFluids.STARLIGHT.get(),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noLootTable()));

    // 自稳定方块:PD 混合 + 倾斜速度自适应增益调度,互斥调节 MASS_TIER(1..16)/LIFT_TIER(1..16)维持载具水平。
    // 红石仅使能(signal=0 停用);右键 ScrollValueBehaviour 调死区 0..30°(默认 3°)。详见 StabilizerBlockEntity Javadoc。
    public static final DeferredHolder<Block, StabilizerBlock> STABILIZER_BLOCK =
            BLOCKS.register("stabilizer",
                    () -> new StabilizerBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StabilizerBlockEntity>> STABILIZER_BE =
            BLOCK_ENTITIES.register("stabilizer",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createStabilizerBlockEntity, STABILIZER_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<Item, BlockItem> STABILIZER_ITEM =
            ModItems.ITEMS.register("stabilizer",
                    () -> new BlockItem(STABILIZER_BLOCK.get(), new Item.Properties()));

    // 虚空软管滑轮: 继承 Create HosePulleyBlock, BE 换成 VoidHosePulleyBlockEntity(自带无限星空液体 handler)。
    // 外观/动画复用 Create 的 hose_pulley model + HosePulleyRenderer。在末地之海区域(末端 y<=startY)时
    // 经 capability 暴露的 VoidHosePulleyFluidHandler.drain 返回无限 STARLIGHT(见 ModCapabilities)。
    public static final DeferredHolder<Block, VoidHosePulleyBlock> VOID_HOSE_PULLEY_BLOCK =
            BLOCKS.register("void_hose_pulley",
                    () -> new VoidHosePulleyBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoidHosePulleyBlockEntity>> VOID_HOSE_PULLEY_BE =
            BLOCK_ENTITIES.register("void_hose_pulley",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createVoidHosePulleyBlockEntity, VOID_HOSE_PULLEY_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<Item, BlockItem> VOID_HOSE_PULLEY_ITEM =
            ModItems.ITEMS.register("void_hose_pulley",
                    () -> new BlockItem(VOID_HOSE_PULLEY_BLOCK.get(), new Item.Properties()));

    // 星空机壳: 继承 Create CasingBlock(铜机壳改色), 有 CT 连接纹理 + 可套管道(见 StarlightEncasedPipeBlock).
    // 合成: 手持星空液体瓶右键铜机壳 / 机械手 / 注液器 250mb starlight 注液. 详见 Feature 9.
    public static final DeferredHolder<Block, CasingBlock> STARLIGHT_CASING_BLOCK =
            BLOCKS.register("starlight_casing",
                    () -> new CasingBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Item, BlockItem> STARLIGHT_CASING_ITEM =
            ModItems.ITEMS.register("starlight_casing",
                    () -> new BlockItem(STARLIGHT_CASING_BLOCK.get(), new Item.Properties()));

    // 星空套壳管道: 继承 EncasedPipeBlock(见 StarlightEncasedPipeBlock), 覆盖 getBlockEntityType 指向自注册 BE.
    // starlight_casing 右键 fluid_pipe -> 此方块; 扳手拆除还原 fluid_pipe(继承 onWrenched). 无 BlockItem(不可直接放置).
    public static final DeferredHolder<Block, StarlightEncasedPipeBlock> STARLIGHT_ENCASED_FLUID_PIPE_BLOCK =
            BLOCKS.register("starlight_encased_fluid_pipe",
                    () -> new StarlightEncasedPipeBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops(),
                            STARLIGHT_CASING_BLOCK::get));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidPipeBlockEntity>> STARLIGHT_ENCASED_FLUID_PIPE_BE =
            BLOCK_ENTITIES.register("starlight_encased_fluid_pipe",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createStarlightEncasedFluidPipeBlockEntity,
                                    STARLIGHT_ENCASED_FLUID_PIPE_BLOCK.get())
                            .build(null));

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
            BLOCK_ENTITIES.register("redstone_counterweight",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createRedstoneCounterweightBlockEntity,
                                    COUNTERWEIGHT_REDSTONE_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RedstoneCounterweightLightBlockEntity>> REDSTONE_COUNTERWEIGHT_LIGHT_BE =
            BLOCK_ENTITIES.register("redstone_counterweight_light",
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

    private static VoidHosePulleyBlockEntity createVoidHosePulleyBlockEntity(BlockPos pos, BlockState state) {
        return new VoidHosePulleyBlockEntity(VOID_HOSE_PULLEY_BE.get(), pos, state);
    }

    // FluidPipeBlockEntity 构造是 3 参数(type,pos,state), 而 BlockEntityType.Builder.of 需要
    // 2 参数 BlockEntitySupplier(pos,state), 不能直接 method ref -> 工厂方法补上 BE type.
    private static FluidPipeBlockEntity createStarlightEncasedFluidPipeBlockEntity(BlockPos pos, BlockState state) {
        return new FluidPipeBlockEntity(STARLIGHT_ENCASED_FLUID_PIPE_BE.get(), pos, state);
    }
}
