package icu.dreamripples.aero_suite.mixin.common;

import java.util.Collection;
import java.util.List;

import icu.dreamripples.aero_suite.common.client.AeroSuiteCreativeBannerLayout;
import icu.dreamripples.aero_suite.common.registry.ModCreativeTabs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * 在 vanilla 填充完创造栏内容后,把 displayItems 替换为插入了横幅空行的版本。
 * 只作用于本 mod 的主创造页。空行只进 displayItems(分页渲染),不进搜索列表。
 */
@Mixin(CreativeModeTab.class)
public class CreativeTabBannerPaddingMixin {
    @Shadow
    private Collection<ItemStack> displayItems;

    @Inject(method = "buildContents", at = @At("RETURN"))
    private void aeroSuite$reserveBannerRows(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        if ((Object) this != ModCreativeTabs.MAIN_TAB.get()) {
            return;
        }
        displayItems = AeroSuiteCreativeBannerLayout.rebuildDisplayItems(List.copyOf(displayItems));
    }
}
