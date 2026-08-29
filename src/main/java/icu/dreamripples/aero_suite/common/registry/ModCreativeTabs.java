package icu.dreamripples.aero_suite.common.registry;

import icu.dreamripples.aero_suite.gravity.GravityVisualization;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GravityVisualization.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gravity_visualization.main"))
                    .icon(() -> ModItems.SPARK_WAND.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // 按 mod 分三段,段间横幅由 CreativeTabBannerPaddingMixin 插入空行,
                        // 每段物品数必须与 AeroSuiteCreativeBannerLayout.SECTION_ITEM_COUNTS 一致。
                        // --- gravity_visualization (9) ---
                        output.accept(ModItems.SPARK_WAND.get());
                        output.accept(ModItems.PORTABLE_DIAGRAM.get());
                        output.accept(ModBlocks.COUNTERWEIGHT_ITEM.get());
                        output.accept(ModBlocks.COUNTERWEIGHT_LIGHT_ITEM.get());
                        output.accept(ModBlocks.COUNTERWEIGHT_LIGHT_PEARL_ITEM.get());
                        output.accept(ModBlocks.COUNTERWEIGHT_REDSTONE_ITEM.get());
                        output.accept(ModBlocks.COUNTERWEIGHT_LIGHT_REDSTONE_ITEM.get());
                        output.accept(ModBlocks.COUNTERWEIGHT_LIGHT_PEARL_REDSTONE_ITEM.get());
                        output.accept(ModBlocks.STABILIZER_ITEM.get());
                        // --- simplification_related (6) ---
                        output.accept(ModBlocks.CONVENIENT_ANALOG_TRANSMISSION_ITEM.get());
                        output.accept(ModBlocks.VARIABLE_SPEED_PORTABLE_ENGINE_ITEMS.get(DyeColor.RED).get());
                        output.accept(ModBlocks.SEQUENTIAL_FEEDER_ITEM.get());
                        output.accept(ModBlocks.SINGLE_SLOT_HOPPER_ITEM.get());
                        output.accept(ModBlocks.FILTERED_SINGLE_SLOT_HOPPER_ITEM.get());
                        output.accept(ModBlocks.ADDRESSING_SIGN_ITEM.get());
                        // --- starlight_logistics (9) ---
                        output.accept(ModItems.STARLIGHT_BUCKET.get());
                        output.accept(ModItems.STARLIGHT_BOTTLE.get());
                        output.accept(ModItems.ACTIVATED_ENDER_PEARL.get());
                        output.accept(ModBlocks.LIGHTWEIGHT_GLASS_ITEM.get());
                        output.accept(ModBlocks.ULTRALIGHT_GLASS_ITEM.get());
                        output.accept(ModBlocks.STARLIGHT_CASING_ITEM.get());
                        output.accept(ModBlocks.VOID_HOSE_PULLEY_ITEM.get());
                        output.accept(ModBlocks.WORLD_ANCHOR_ITEM.get());
                        output.accept(ModBlocks.PEARL_STASIS_ITEM.get());
                        output.accept(ModItems.VOID_TOUCH_SMITHING_TEMPLATE.get());
                        output.accept(ModItems.SPACE_SHARD.get());
                        // zinc_lump 改放在 Create 的 base 创造页(zinc_nugget 旁),
                        // 见 event/CreativeTabInjections.java
                    })
                    .build());
}
