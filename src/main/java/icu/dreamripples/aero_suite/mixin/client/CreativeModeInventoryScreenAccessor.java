package icu.dreamripples.aero_suite.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {
    @Accessor("selectedTab")
    static CreativeModeTab aeroSuite$getSelectedTab() {
        throw new AssertionError();
    }

    @Accessor("scrollOffs")
    float aeroSuite$getScrollOffset();
}
