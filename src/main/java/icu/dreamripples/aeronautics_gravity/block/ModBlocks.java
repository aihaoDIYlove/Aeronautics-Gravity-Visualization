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

    private static ConvenientAnalogTransmissionBlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ConvenientAnalogTransmissionBlockEntity(CONVENIENT_ANALOG_TRANSMISSION_BE.get(), pos, state);
    }
}
