package icu.dreamripples.aeronautics_gravity.item;

import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import icu.dreamripples.aeronautics_gravity.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AeronauticsGravityVisualization.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aeronautics_gravity.main"))
                    .icon(() -> ModItems.SPARK_WAND.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SPARK_WAND.get());
                        output.accept(ModBlocks.CONVENIENT_ANALOG_TRANSMISSION_ITEM.get());
                        output.accept(ModBlocks.COUNTERWEIGHT_ITEM.get());
                        output.accept(ModBlocks.COUNTERWEIGHT_LIGHT_ITEM.get());
                        output.accept(ModBlocks.COUNTERWEIGHT_LIGHT_PEARL_ITEM.get());
                        output.accept(ModBlocks.COUNTERWEIGHT_REDSTONE_ITEM.get());
                        output.accept(ModBlocks.COUNTERWEIGHT_LIGHT_REDSTONE_ITEM.get());
                        output.accept(ModBlocks.COUNTERWEIGHT_LIGHT_PEARL_REDSTONE_ITEM.get());
                        output.accept(ModBlocks.LIGHTWEIGHT_GLASS_ITEM.get());
                        output.accept(ModBlocks.ULTRALIGHT_GLASS_ITEM.get());
                        output.accept(ModBlocks.STABILIZER_ITEM.get());
                        output.accept(ModBlocks.WORLD_ANCHOR_ITEM.get());
                        output.accept(ModItems.INCOMPLETE_WORLD_ANCHOR.get());
                        output.accept(ModItems.INCOMPLETE_STABILIZER.get());
                        output.accept(ModItems.INCOMPLETE_COUNTERWEIGHT.get());
                        output.accept(ModItems.INCOMPLETE_COUNTERWEIGHT_LIGHT.get());
                        output.accept(ModItems.STARLIGHT_BUCKET.get());
                        output.accept(ModItems.STARLIGHT_BOTTLE.get());
                        output.accept(ModBlocks.VOID_HOSE_PULLEY_ITEM.get());
                        output.accept(ModBlocks.STARLIGHT_CASING_ITEM.get());
                        output.accept(ModItems.ZINC_LUMP.get());
                    })
                    .build());
}
