package icu.dreamripples.aeronautics_gravity.client;

import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import icu.dreamripples.aeronautics_gravity.block.SequentialFeederMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 顺序供料器 Screen -- 与 SequentialFeederMenu.addSlots 坐标严格对齐。
 *
 * <pre>
 * 标记槽 y=33:  [?] [?] [?] ... (9 格,幽灵)
 * 箭头   y=52:       ↑(位于当前标记槽内)
 * 物品槽 y=78:  [ ] [ ] [ ] ... (9 格,实体;未标记时隐藏)
 * 玩家栏 y=106:      3 行背包 + 快捷栏
 * </pre>
 */
public class SequentialFeederScreen extends AbstractSimiContainerScreen<SequentialFeederMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AeronauticsGravityVisualization.MOD_ID, "textures/gui/sequential_feeder.png");

    // 贴图右上角三种颜色箭头精灵(均为 8x9)
    private static final int ARROW_W = 8, ARROW_H = 9;
    private static final int ARROW_RED_U = 230, ARROW_RED_V = 0;
    private static final int ARROW_YELLOW_U = 239, ARROW_YELLOW_V = 0;
    private static final int ARROW_GREEN_U = 248, ARROW_GREEN_V = 0;

    // 与 SequentialFeederMenu.addSlots 的坐标一致(修改时两边同步!)
    private static final int SLOT_X = 16;
    private static final int ARROW_Y = 52;
    private static final int PLAYER_LABEL_Y = 70;

    public SequentialFeederScreen(SequentialFeederMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        // 208 宽 + 203 高
        setWindowSize(208, 203);
        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // 手绘 GUI 背景
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        // 标题 & 玩家栏标签
        int titleColor = 0x593A2A;
        graphics.drawString(font, this.title, x + 82, y + 4, titleColor, false);
        graphics.drawString(font, this.playerInventoryTitle, x + 8, y + PLAYER_LABEL_Y, titleColor, false);

        // 着色箭头指向当前步
        int step = menu.contentHolder.feederData.get(0);
        boolean used = menu.contentHolder.feederData.get(1) != 0;
        int slotCenterX = x + SLOT_X + step * 20 + 8;
        drawArrow(graphics, slotCenterX, y + ARROW_Y, step, used);
    }

    /** 从贴图右上角 blit 对应颜色箭头精灵,8x9,水平居中于当前步槽。 */
    private void drawArrow(GuiGraphics graphics, int cx, int y, int step, boolean used) {
        ItemStack current = menu.getSlot(SequentialFeederMenu.ITEM_SLOTS_START + step).getItem();
        int u, v;
        if (current.isEmpty()) {
            u = ARROW_RED_U;
            v = ARROW_RED_V;   // 缺货(含未标记)
        } else if (used) {
            u = ARROW_GREEN_U;
            v = ARROW_GREEN_V; // 本步已取走
        } else {
            u = ARROW_YELLOW_U;
            v = ARROW_YELLOW_V; // 有货待取
        }
        graphics.blit(TEXTURE, cx - ARROW_W / 2, y, u, v, ARROW_W, ARROW_H, 256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
