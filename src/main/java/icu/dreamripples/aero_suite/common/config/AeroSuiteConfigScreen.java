package icu.dreamripples.aero_suite.common.config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Aeronautics Suite 自绘配置屏(替代 Catnip BaseConfigScreen -- 后者不读 lang 文件无法本地化)。
 *
 * <p>两级菜单 + 即时保存:
 * <ul>
 *   <li><b>一级</b>: 三个分组入口(航空学:重力可视化/方便物品/星空物流) + 重置所有设置 + 返回至模组页。</li>
 *   <li><b>二级</b>: 标题 + "启用/禁用全部"主开关行 + 手写滚动列表(图标/标签/开关行, 点击即生效) +
 *       底部唯一的"返回至上级菜单"按钮。</li>
 * </ul>
 *
 * <p>开关改动即时落盘({@code ConfigBool.set})并 {@link FeatureGates#invalidate()} 重建缓存;
 * 纯配方开关(产物为 vanilla/其他 mod)改动后提示一次需 {@code /reload} --
 * {@link FeatureEnabledCondition} 在数据包加载时求值。
 * 配置屏只在客户端打开, 多人下改动只写本侧文件 -- 与原 Catnip 屏一致。
 */
public class AeroSuiteConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int TOP_MARGIN = 64;
    private static final int BOTTOM_MARGIN = 44;

    private enum Mode { MAIN, FEATURES }

    private final Screen parent;
    private Mode mode = Mode.MAIN;
    private AeroSuiteFeatures.Group page;
    private double scrollOffset;
    private int hoveredIndex = -1;
    private boolean reloadHintShown;

    public AeroSuiteConfigScreen(Screen parent) {
        super(Component.translatable("aero_suite.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (mode == Mode.MAIN)
            initMain();
        else
            initFeatures();
    }

    // ── 一级菜单 ──────────────────────────────────────────────

    private void initMain() {
        int bw = 260, bh = 22, gap = 8;
        int y = 60;
        for (AeroSuiteFeatures.Group g : AeroSuiteFeatures.Group.values()) {
            addRenderableWidget(Button.builder(Component.translatable("aero_suite.group." + g.id), b -> {
                page = g;
                mode = Mode.FEATURES;
                scrollOffset = 0;
                init();
            }).bounds((this.width - bw) / 2, y, bw, bh).build());
            y += bh + gap;
        }
        y += gap;
        addRenderableWidget(Button.builder(Component.translatable("aero_suite.config.reset_all"), b -> {
            for (AeroSuiteFeatures.Feature f : AeroSuiteFeatures.ALL)
                setKey(f, f.defaultValue());
            scrollOffset = 0;
        }).bounds((this.width - bw) / 2, y, bw, bh).build());
        y += bh + gap;
        addRenderableWidget(Button.builder(Component.translatable("aero_suite.config.back_to_mods"),
                b -> onClose()).bounds((this.width - bw) / 2, y, bw, bh).build());
    }

    // ── 二级菜单 ──────────────────────────────────────────────

    private void initFeatures() {
        int bw = 200, bh = 20;
        addRenderableWidget(Button.builder(Component.translatable("aero_suite.config.back"),
                b -> { mode = Mode.MAIN; init(); })
                .bounds((this.width - bw) / 2, this.height - 28, bw, bh).build());
    }

    private List<AeroSuiteFeatures.Feature> pageFeatures() {
        List<AeroSuiteFeatures.Feature> list = new ArrayList<>();
        for (AeroSuiteFeatures.Feature f : AeroSuiteFeatures.ALL)
            if (f.group() == page) list.add(f);
        return list;
    }

    /** 即时保存: 写 toml + 重建门控缓存; 纯配方开关改动提示一次 /reload。 */
    private void setKey(AeroSuiteFeatures.Feature f, boolean value) {
        if (FeatureGates.CONFIG == null || FeatureGates.isEnabled(f.key()) == value)
            return;
        FeatureGates.CONFIG.byKey.get(f.key()).set(value);
        FeatureGates.invalidate();
        if (!f.deletesItems() && !reloadHintShown) {
            reloadHintShown = true;
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.displayClientMessage(
                        Component.translatable("aero_suite.config.reload_hint").withStyle(ChatFormatting.YELLOW), false);
        }
    }

    /** 本页是否全开(主开关状态)。 */
    private boolean pageAllOn() {
        for (AeroSuiteFeatures.Feature f : pageFeatures())
            if (!FeatureGates.isEnabled(f.key()))
                return false;
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (mode == Mode.MAIN) {
            graphics.drawCenteredString(this.font, this.title, this.width / 2, 24, 0xFFFFFF);
            return;
        }

        graphics.drawCenteredString(this.font,
                Component.translatable("aero_suite.group." + page.id), this.width / 2, 12, 0xFFFFFF);

        int rowW = Math.min(420, this.width - 60);
        int rowX = (this.width - rowW) / 2;

        // 头部: "启用/禁用全部" + 主开关
        int headY = 34;
        boolean allOn = pageAllOn();
        boolean headHover = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= headY && mouseY < headY + ROW_HEIGHT - 2;
        graphics.fill(rowX, headY, rowX + rowW, headY + ROW_HEIGHT - 2, headHover ? 0x60FFFFFF : 0x30000000);
        graphics.drawString(this.font, Component.translatable("aero_suite.config.toggle_all"),
                rowX + 8, headY + 8, 0xFFFFFF);
        drawState(graphics, rowX, rowW, headY, allOn);

        // 列表
        List<AeroSuiteFeatures.Feature> features = pageFeatures();
        int listTop = TOP_MARGIN;
        int listBottom = this.height - BOTTOM_MARGIN;
        int listHeight = listBottom - listTop;
        int visibleRows = Math.max(1, listHeight / ROW_HEIGHT);
        int maxOffset = Math.max(0, features.size() - visibleRows);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxOffset);

        hoveredIndex = -1;
        for (int i = 0; i < visibleRows && (int) scrollOffset + i < features.size(); i++) {
            int row = (int) scrollOffset + i;
            AeroSuiteFeatures.Feature f = features.get(row);
            int y = listTop + i * ROW_HEIGHT;
            boolean hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            if (hovered) hoveredIndex = row;

            graphics.fill(rowX, y, rowX + rowW, y + ROW_HEIGHT - 2, hovered ? 0x60FFFFFF : 0x30000000);
            Item icon = f.icon().get();
            if (icon != null)
                graphics.renderItem(new ItemStack(icon), rowX + 4, y + 4);
            graphics.drawString(this.font, Component.translatable("aero_suite.feature." + f.key()),
                    rowX + 28, y + 8, 0xFFFFFF);
            drawState(graphics, rowX, rowW, y, FeatureGates.isEnabled(f.key()));
        }

        // tooltip(悬浮行)
        if (hoveredIndex >= 0 && mouseX >= rowX && mouseX < rowX + rowW) {
            AeroSuiteFeatures.Feature f = features.get(hoveredIndex);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("aero_suite.feature." + f.key()).withStyle(ChatFormatting.GOLD));
            lines.addAll(wrap(Component.translatable("aero_suite.feature." + f.key() + ".desc").getString(), 240));
            lines.add(Component.translatable(f.deletesItems()
                    ? "aero_suite.config.item_gate" : "aero_suite.config.recipe_gate")
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal(f.key()).withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    private void drawState(GuiGraphics graphics, int rowX, int rowW, int y, boolean on) {
        String state = on ? "■" : "□";
        graphics.drawString(this.font, Component.translatable(on ? "aero_suite.config.on" : "aero_suite.config.off"),
                rowX + rowW - 30, y + 8, on ? 0x55FF55 : 0xFF5555);
        graphics.drawString(this.font, state, rowX + rowW - 46, y + 7, on ? 0x55FF55 : 0xFF5555);
    }

    /** 按像素宽度做字符串折行(font.split 返回 FormattedCharSequence, 不能与 Component tooltip 混排)。 */
    private List<Component> wrap(String text, int maxWidth) {
        List<Component> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int w = 0;
        for (String ch : text.split("", -1)) { // 逐字符(中文无空格)
            int cw = this.font.width(ch);
            if (w + cw > maxWidth && line.length() > 0) {
                lines.add(Component.literal(line.toString()).withStyle(ChatFormatting.GRAY));
                line.setLength(0);
                w = 0;
            }
            line.append(ch);
            w += cw;
        }
        if (line.length() > 0)
            lines.add(Component.literal(line.toString()).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rowW = Math.min(420, this.width - 60);
        int rowX = (this.width - rowW) / 2;

        if (mode == Mode.MAIN)
            return super.mouseClicked(mouseX, mouseY, button);

        // 头部主开关: 任一关 -> 全开; 全开 -> 全关
        int headY = 34;
        if (mouseX >= rowX && mouseX < rowX + rowW && mouseY >= headY && mouseY < headY + ROW_HEIGHT - 2) {
            boolean target = !pageAllOn();
            for (AeroSuiteFeatures.Feature f : pageFeatures())
                setKey(f, target);
            return true;
        }

        List<AeroSuiteFeatures.Feature> features = pageFeatures();
        int listTop = TOP_MARGIN, listBottom = this.height - BOTTOM_MARGIN;
        if (mouseX >= rowX && mouseX < rowX + rowW && mouseY >= listTop && mouseY < listBottom) {
            int i = (int) ((mouseY - listTop) / ROW_HEIGHT + scrollOffset);
            if (i >= 0 && i < features.size()) {
                AeroSuiteFeatures.Feature f = features.get(i);
                setKey(f, !FeatureGates.isEnabled(f.key()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && mode == Mode.FEATURES) {
            scrollOffset = Math.max(0, scrollOffset - scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null)
            this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
