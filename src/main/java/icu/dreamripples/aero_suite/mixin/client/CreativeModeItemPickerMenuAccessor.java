package icu.dreamripples.aero_suite.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public interface CreativeModeItemPickerMenuAccessor {
    @Invoker("getRowIndexForScroll")
    int aeroSuite$getRowIndexForScroll(float scrollOffset);
}
