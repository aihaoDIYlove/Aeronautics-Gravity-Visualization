package icu.dreamripples.aero_suite.simplification.client;

import icu.dreamripples.aero_suite.simplification.block.SingleSlotHopperMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import icu.dreamripples.aero_suite.simplification.SimplificationRelated;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 单格漏斗界面(占位): 暂用原版漏斗贴图, 槽位居中(80,20)与菜单一致。
 * 正式贴图后只需换 TEXTURE 并按新图调 imageWidth/Height。
 */
public class SingleSlotHopperScreen extends AbstractContainerScreen<SingleSlotHopperMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SimplificationRelated.MOD_ID,
                    "textures/gui/single_slot_hopper.png");

    public SingleSlotHopperScreen(SingleSlotHopperMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 133;
    }

    // 上半部分为透明绘制, 标题/物品栏标签都省去
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }
}
