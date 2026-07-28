package icu.dreamripples.aeronautics_gravity.block;

import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import icu.dreamripples.aeronautics_gravity.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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

    public static final DeferredHolder<Block, CounterweightBlock> COUNTERWEIGHT_COAL_BLOCK =
            BLOCKS.register("counterweight_coal",
                    () -> new CounterweightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, CounterweightBlock> COUNTERWEIGHT_GOLD_BLOCK =
            BLOCKS.register("counterweight_gold",
                    () -> new CounterweightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .requiresCorrectToolForDrops()));

    // 三种配重块共用一个 BE 类型(同 CounterweightBlockEntity + MASS_TIER),省去重复注册
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CounterweightBlockEntity>> COUNTERWEIGHT_BE =
            BLOCK_ENTITIES.register("counterweight",
                    () -> BlockEntityType.Builder
                            .of(ModBlocks::createCounterweightBlockEntity,
                                    COUNTERWEIGHT_BLOCK.get(),
                                    COUNTERWEIGHT_COAL_BLOCK.get(),
                                    COUNTERWEIGHT_GOLD_BLOCK.get())
                            .build(null));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_ITEM =
            ModItems.ITEMS.register("counterweight",
                    () -> new BlockItem(COUNTERWEIGHT_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_COAL_ITEM =
            ModItems.ITEMS.register("counterweight_coal",
                    () -> new BlockItem(COUNTERWEIGHT_COAL_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_GOLD_ITEM =
            ModItems.ITEMS.register("counterweight_gold",
                    () -> new BlockItem(COUNTERWEIGHT_GOLD_BLOCK.get(), new Item.Properties()));

    // 红石配重块系列:铁/煤/金三种皮,共用 RedstoneCounterweightBlock(MASS_TIER 1..16),红stone信号驱动。
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

    public static final DeferredHolder<Block, RedstoneCounterweightBlock> COUNTERWEIGHT_COAL_REDSTONE_BLOCK =
            BLOCKS.register("counterweight_coal_redstone",
                    () -> new RedstoneCounterweightBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .sound(SoundType.COPPER)
                                    .strength(3.5f)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, RedstoneCounterweightBlock> COUNTERWEIGHT_GOLD_REDSTONE_BLOCK =
            BLOCKS.register("counterweight_gold_redstone",
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

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_COAL_REDSTONE_ITEM =
            ModItems.ITEMS.register("counterweight_coal_redstone",
                    () -> new BlockItem(COUNTERWEIGHT_COAL_REDSTONE_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> COUNTERWEIGHT_GOLD_REDSTONE_ITEM =
            ModItems.ITEMS.register("counterweight_gold_redstone",
                    () -> new BlockItem(COUNTERWEIGHT_GOLD_REDSTONE_BLOCK.get(), new Item.Properties()));

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
                                    COUNTERWEIGHT_REDSTONE_BLOCK.get(),
                                    COUNTERWEIGHT_COAL_REDSTONE_BLOCK.get(),
                                    COUNTERWEIGHT_GOLD_REDSTONE_BLOCK.get())
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
}
