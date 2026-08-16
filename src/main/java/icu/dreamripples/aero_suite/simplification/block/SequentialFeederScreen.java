package icu.dreamripples.aero_suite.client;

import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import icu.dreamripples.aero_suite.AeronauticsGravityVisualization;
import icu.dreamripples.aero_suite.block.SequentialFeederBlockEntity;
import icu.dreamripples.aero_suite.block.SequentialFeederMenu;
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

    // 贴图右上角四种颜色箭头精灵(均为 8x9)
    private static final int ARROW_W = 8, ARROW_H = 9;
    private static final int ARROW_GRAY_U = 221, ARROW_GRAY_V = 0;   // WAITING 未就绪(等待授权)
    private static final int ARROW_RED_U = 230, ARROW_RED_V = 0;     // ARMED 就绪但缺货
    private static final int ARROW_YELLOW_U = 239, ARROW_YELLOW_V = 0; // ARMED 就绪待取
    private static final int ARROW_GREEN_U = 248, ARROW_GREEN_V = 0; // TAKEN 已取走(等下一脉冲)

    // 与 SequentialFeederMenu.addSlots 的坐标一致(修改时两边同步!)
    private static final int SLOT_X = 16;
    private static final int ARROW_Y = 52;
    private static final int MARKER_LABEL_Y = 22;
    private static final int INVENTORY_LABEL_Y = 67;

    private final Component markerLabel;
    private final Component inventoryLabel;

    public SequentialFeederScreen(SequentialFeederMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.markerLabel = Component.translatable("gui.aeronautics_gravity.sequential_feeder.marker_slots");
        this.inventoryLabel = Component.translatable("gui.aeronautics_gravity.sequential_feeder.inventory_slots");
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

        // 标题 & 分区标签
        int titleColor = 0x593A2A;
        graphics.drawString(font, this.title, x + 82, y + 4, titleColor, false);
        graphics.drawString(font, markerLabel, x + 16, y + MARKER_LABEL_Y, titleColor, false);
        graphics.drawString(font, inventoryLabel, x + 16, y + INVENTORY_LABEL_Y, titleColor, false);

        // 着色箭头指向当前步
        int step = menu.contentHolder.feederData.get(0);
        int stateOrd = menu.contentHolder.feederData.get(1);
        int slotCenterX = x + SLOT_X + step * 20 + 8;
        drawArrow(graphics, slotCenterX, y + ARROW_Y, step, stateOrd);
    }

    /** 从贴图右上角 blit 对应颜色箭头精灵,8x9,水平居中于当前步槽。 */
    private void drawArrow(GuiGraphics graphics, int cx, int y, int step, int stateOrd) {
        int u, v;
        if (stateOrd == SequentialFeederBlockEntity.StepState.WAITING.ordinal()) {
            u = ARROW_GRAY_U;
            v = ARROW_GRAY_V;   // 未就绪: 等待红石授权(脉冲模式)
        } else if (stateOrd == SequentialFeederBlockEntity.StepState.TAKEN.ordinal()) {
            u = ARROW_GREEN_U;
            v = ARROW_GREEN_V;  // 已取走: 等下一脉冲前进
        } else {
            ItemStack current = menu.getSlot(SequentialFeederMenu.ITEM_SLOTS_START + step).getItem();
            if (current.isEmpty()) {
                u = ARROW_RED_U;
                v = ARROW_RED_V; // 就绪但缺货(含未标记)
            } else {
                u = ARROW_YELLOW_U;
                v = ARROW_YELLOW_V; // 就绪待取
            }
        }
        graphics.blit(TEXTURE, cx - ARROW_W / 2, y, u, v, ARROW_W, ARROW_H, 256, 256);
    }

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        // 槽位网格间距 20px(贴图),vanilla 命中区只有 16px 槽 ±1px = 18px,
        // 每格会留 2px 死区,点在槽边缘时点击被吞(表现为"放不进去要多点几下")。
        // 放宽到整 20px 网格,消除死区。
        return super.isHovering(x - 1, y - 1, width + 2, height + 2, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
