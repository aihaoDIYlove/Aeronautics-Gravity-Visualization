package icu.dreamripples.aero_suite.common.client;
import icu.dreamripples.aero_suite.common.AeroSuiteIds;
import icu.dreamripples.aero_suite.common.registry.ModCreativeTabs;
import icu.dreamripples.aero_suite.mixin.client.CreativeModeInventoryScreenAccessor;
import icu.dreamripples.aero_suite.mixin.client.CreativeModeItemPickerMenuAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

/**
 * 创造栏横幅渲染
 * 布局见 {@link AeroSuiteCreativeBannerLayout}。静态单帧,无动画。
 * <p>
 * 滚动同步:通过 Accessor 调 vanilla 私有 {@code getRowIndexForScroll(scrollOffs)}
 * 得到第一可见行,横幅行号减它得可见行号,超出 5 行视野则跳过——横幅随物品一起滚动。
 */
@EventBusSubscriber(modid = AeroSuiteIds.GRAVITY_ID, value = Dist.CLIENT)
public final class AeroSuiteCreativeBanner {
    private static final int BANNER_WIDTH = 162;
    private static final int VISIBLE_ROW_COUNT = 5;
    private static final int BANNER_X_OFFSET = 8;
    private static final int BANNER_Y_OFFSET = 17;
    private static final int TITLE_X_OFFSET = BANNER_X_OFFSET + 5;
    private static final int TITLE_Y_INSET = 5;

    @SubscribeEvent
    static void onRenderForeground(ContainerScreenEvent.Render.Foreground event) {
        if (!(event.getContainerScreen() instanceof CreativeModeInventoryScreen screen)
                || CreativeModeInventoryScreenAccessor.aeroSuite$getSelectedTab()
                        != ModCreativeTabs.MAIN_TAB.get()) {
            return;
        }

        CreativeModeInventoryScreenAccessor screenAccessor = (CreativeModeInventoryScreenAccessor) screen;
        CreativeModeItemPickerMenuAccessor menuAccessor =
                (CreativeModeItemPickerMenuAccessor) screen.getMenu();
        int firstVisibleRow = menuAccessor.aeroSuite$getRowIndexForScroll(
                screenAccessor.aeroSuite$getScrollOffset());

        for (AeroSuiteCreativeBannerLayout.Section section : AeroSuiteCreativeBannerLayout.SECTIONS) {
            int visibleRow = section.bannerRow() - firstVisibleRow;
            if (visibleRow < 0 || visibleRow >= VISIBLE_ROW_COUNT) {
                continue;
            }
            int relativeY = BANNER_Y_OFFSET + visibleRow * AeroSuiteCreativeBannerLayout.FRAME_HEIGHT;
            event.getGuiGraphics().blit(section.texture(), BANNER_X_OFFSET, relativeY,
                    BANNER_WIDTH, AeroSuiteCreativeBannerLayout.FRAME_HEIGHT,
                    0.0F, 0.0F, BANNER_WIDTH, AeroSuiteCreativeBannerLayout.FRAME_HEIGHT,
                    BANNER_WIDTH, AeroSuiteCreativeBannerLayout.FRAME_HEIGHT);
            drawTitle(event.getGuiGraphics(), section.title(), visibleRow);
        }
    }

    /** 白色标题直接画在横幅贴图上,无衬底。 */
    private static void drawTitle(GuiGraphics graphics, net.minecraft.network.chat.Component title,
            int visibleRow) {
        Font font = Minecraft.getInstance().font;
        int bannerY = BANNER_Y_OFFSET + visibleRow * AeroSuiteCreativeBannerLayout.FRAME_HEIGHT;
        graphics.drawString(font, title, TITLE_X_OFFSET, bannerY + TITLE_Y_INSET, 0xFFFFFF, true);
    }

    private AeroSuiteCreativeBanner() {}
}
