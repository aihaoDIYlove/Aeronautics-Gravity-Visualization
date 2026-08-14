package icu.dreamripples.aeronautics_gravity.client;

import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import icu.dreamripples.aeronautics_gravity.block.SequentialFeederBlockEntity;
import icu.dreamripples.aeronautics_gravity.block.SequentialFeederMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 顺序供料器 Screen(v1 占位 UI,无自定义贴图 -- 整体测完用户补自制贴图)。
 * 面板/槽框/箭头全程序绘制(GuiGraphics.fill),布局与 SequentialFeederMenu.addSlots 对齐:
 *
 * <pre>
 * 标记槽 y=18:  [?] [?] [?] ... (9 格,幽灵)
 * 物品槽 y=54:  [ ] [ ] [ ] ... (9 格,实体;未标记时隐藏)
 * 箭头   y=36:       ↑(着色: 绿=本步已取走 黄=待取 红=缺货)
 * 玩家栏 y=116
 * </pre>
 *
 * 箭头颜色派生自 [currentStep, stepOutputUsed, inventory[currentStep]](ContainerData
 * + 菜单槽同步),不存字段,永不过期。
 */
public class SequentialFeederScreen extends AbstractSimiContainerScreen<SequentialFeederMenu> {

    private static final int COLOR_BG = 0xFFC6C6C6;       // 面板灰(vanilla GUI 同款)
    private static final int COLOR_SLOT = 0xFF8B8B8B;     // 槽底
    private static final int COLOR_SLOT_DARK = 0xFF373737; // 槽内阴影
    private static final int COLOR_ARROW_GREEN = 0xFF3FBF3F; // 已取走
    private static final int COLOR_ARROW_YELLOW = 0xFFE9E24B; // 待取/有货
    private static final int COLOR_ARROW_RED = 0xFFE33B3B;    // 缺货

    // 与 SequentialFeederMenu.addSlots 的坐标一致
    private static final int SLOT_X = 8;
    private static final int MARKER_Y = 18;
    private static final int ITEM_Y = 54;
    private static final int ARROW_Y = 36;

    public SequentialFeederScreen(SequentialFeederMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        setWindowSize(176, 166);
        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // 面板底
        graphics.fill(x, y, x + imageWidth, y + imageHeight, COLOR_BG);

        // 标记槽(9)
        for (int i = 0; i < 9; i++)
            drawSlotBg(graphics, x + SLOT_X + i * 18, y + MARKER_Y);
        // 物品槽(9)
        for (int i = 0; i < 9; i++)
            drawSlotBg(graphics, x + SLOT_X + i * 18, y + ITEM_Y);

        // 玩家栏 + 快捷栏
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawSlotBg(graphics, x + 8 + col * 18, y + 116 + row * 18);
        for (int col = 0; col < 9; col++)
            drawSlotBg(graphics, x + 8 + col * 18, y + 174);

        // 标题 & 玩家栏标签
        graphics.drawString(font, this.title, x + 8, y + 6, 0x404040, false);
        graphics.drawString(font, this.playerInventoryTitle, x + 8, y + 112, 0x404040, false);

        // 着色箭头指向当前步(颜色派生自状态)
        int step = menu.contentHolder.feederData.get(0);
        boolean used = menu.contentHolder.feederData.get(1) != 0;
        drawArrow(graphics, x + SLOT_X + step * 18 + 8, y + ARROW_Y, step, used);
    }

    private void drawSlotBg(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, COLOR_SLOT_DARK);
        graphics.fill(x, y, x + 16, y + 16, COLOR_SLOT);
    }

    /**
     * 程序画上指箭头:以 为中心,宽 10 高 8。两个矩形近似三角(梯形拼尖)。
     */
    private void drawArrow(GuiGraphics graphics, int cx, int y, int step, boolean used) {
        ItemStack current = menu.getSlot(SequentialFeederMenu.ITEM_SLOTS_START + step).getItem();
        int color;
        if (current.isEmpty())
            color = COLOR_ARROW_RED;   // 缺货(含未标记: 未标记槽物品槽 isActive=false 但这里槽空)
        else if (used)
            color = COLOR_ARROW_GREEN; // 本步已取走
        else
            color = COLOR_ARROW_YELLOW; // 有货待取
        // 尖(2px 台阶收窄至 0)
        graphics.fill(cx - 1, y, cx + 1, y + 2, color);
        graphics.fill(cx - 3, y + 2, cx + 3, y + 4, color);
        // 杆
        graphics.fill(cx - 2, y + 4, cx + 2, y + 8, color);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
