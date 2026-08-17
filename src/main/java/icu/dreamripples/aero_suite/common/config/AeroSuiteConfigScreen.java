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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aeronautics Suite 自绘配置屏(替代 Catnip BaseConfigScreen -- 后者不读 lang 文件无法本地化)。
 *
 * <p>纯 vanilla widget: 3 个分页 tab(按 {@link AeroSuiteFeatures.Group})+ 手写滚动列表。
 * 每行 = 物品图标 + 本地化标签 + 开关; 悬浮显示 tooltip(描述/开关语义/config key)。
 * 底部: 本页全开/全关/恢复默认 + 保存/取消。
 *
 * <p>保存语义: 逐 key {@code ConfigBool.set}(写入 COMMON toml 并落盘), 然后
 * {@link FeatureGates#invalidate()} 重建缓存; 若改动了任何纯配方开关, 提示需要
 * {@code /reload} 才能让 {@link FeatureEnabledCondition} 重新求值。
 * 配置屏只在客户端打开(单人/本地实例), 多人下改动只写本侧文件 -- 与原 Catnip 屏一致。
 */
public class AeroSuiteConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int TOP_MARGIN = 56;
    private static final int BOTTOM_MARGIN = 64;

    private final Screen parent;
    private final Map<String, Boolean> pending = new HashMap<>();
    private AeroSuiteFeatures.Group page = AeroSuiteFeatures.Group.GRAVITY;
    private double scrollOffset;
    private int hoveredIndex = -1;

    public AeroSuiteConfigScreen(Screen parent) {
        super(Component.translatable("aero_suite.config.title"));
        this.parent = parent;
        for (AeroSuiteFeatures.Feature f : AeroSuiteFeatures.ALL)
            pending.put(f.key(), FeatureGates.CONFIG != null ? FeatureGates.isEnabled(f.key()) : f.defaultValue());
    }

    @Override
    protected void init() {
        int tabW = Math.min(160, (this.width - 40) / 3);
        int tabX = (this.width - tabW * 3 - 10) / 2;
        for (int i = 0; i < AeroSuiteFeatures.Group.values().length; i++) {
            AeroSuiteFeatures.Group g = AeroSuiteFeatures.Group.values()[i];
            addRenderableWidget(Button.builder(Component.translatable("aero_suite.group." + g.id),
                            b -> selectPage(g))
                    .bounds(tabX + i * (tabW + 5), 28, tabW, 20).build());
        }

        int bw = 110, gap = 6;
        int by = this.height - 28;
        int total = bw * 5 + gap * 4;
        int bx = (this.width - total) / 2;
        addRenderableWidget(Button.builder(Component.translatable("aero_suite.config.all_on"), b -> setPageAll(true))
                .bounds(bx, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("aero_suite.config.all_off"), b -> setPageAll(false))
                .bounds(bx += bw + gap, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("aero_suite.config.reset"), b -> resetPage())
                .bounds(bx += bw + gap, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("aero_suite.config.save"), b -> saveAndClose())
                .bounds(bx += bw + gap, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(bx += bw + gap, by, bw, 20).build());
    }

    private void selectPage(AeroSuiteFeatures.Group g) {
        page = g;
        scrollOffset = 0;
    }

    private List<AeroSuiteFeatures.Feature> pageFeatures() {
        List<AeroSuiteFeatures.Feature> list = new ArrayList<>();
        for (AeroSuiteFeatures.Feature f : AeroSuiteFeatures.ALL)
            if (f.group() == page) list.add(f);
        return list;
    }

    private void setPageAll(boolean value) {
        for (AeroSuiteFeatures.Feature f : pageFeatures())
            pending.put(f.key(), value);
    }

    private void resetPage() {
        for (AeroSuiteFeatures.Feature f : pageFeatures())
            pending.put(f.key(), f.defaultValue());
    }

    private void saveAndClose() {
        AeroSuiteConfig config = FeatureGates.CONFIG;
        if (config == null) { // 理论上到不了: mod 列表按钮出现时配置已加载
            onClose();
            return;
        }
        boolean recipeChanged = false;
        for (AeroSuiteFeatures.Feature f : AeroSuiteFeatures.ALL) {
            boolean now = pending.getOrDefault(f.key(), f.defaultValue());
            boolean before = FeatureGates.isEnabled(f.key());
            if (now == before) continue;
            config.byKey.get(f.key()).set(now); // CValue.set: 写内存 + save() 落盘
            if (!f.deletesItems()) recipeChanged = true;
        }
        FeatureGates.invalidate();
        if (recipeChanged) {
            // FeatureEnabledCondition 在数据包加载时求值, 需 /reload 才生效
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.displayClientMessage(
                        Component.translatable("aero_suite.config.reload_hint").withStyle(ChatFormatting.YELLOW), false);
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        List<AeroSuiteFeatures.Feature> features = pageFeatures();
        int listTop = TOP_MARGIN;
        int listBottom = this.height - BOTTOM_MARGIN;
        int listHeight = listBottom - listTop;
        int rowW = Math.min(420, this.width - 60);
        int rowX = (this.width - rowW) / 2;
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

            // 行背景/悬浮高亮
            graphics.fill(rowX, y, rowX + rowW, y + ROW_HEIGHT - 2,
                    hovered ? 0x60FFFFFF : 0x30000000);
            // 图标
            Item icon = f.icon().get();
            if (icon != null)
                graphics.renderItem(new ItemStack(icon), rowX + 4, y + 4);
            // 标签
            boolean on = pending.getOrDefault(f.key(), f.defaultValue());
            graphics.drawString(this.font, Component.translatable("aero_suite.feature." + f.key()),
                    rowX + 28, y + 8, 0xFFFFFF);
            // 开关状态
            String state = on ? "■" : "□";
            graphics.drawString(this.font, Component.translatable(on ? "aero_suite.config.on" : "aero_suite.config.off"),
                    rowX + rowW - 34, y + 8, on ? 0x55FF55 : 0xFF5555);
            graphics.drawString(this.font, state, rowX + rowW - 50, y + 7, on ? 0x55FF55 : 0xFF5555);
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

    /** 按像素宽度做字符串折行(font.split 返回 FormattedCharSequence, 不能与 Component tooltip 混排)。 */
    private List<Component> wrap(String text, int maxWidth) {
        List<Component> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int w = 0;
        for (String word : text.split("", -1)) { // 逐字符(中文无空格)
            int cw = this.font.width(word);
            if (w + cw > maxWidth && line.length() > 0) {
                lines.add(Component.literal(line.toString()).withStyle(ChatFormatting.GRAY));
                line.setLength(0);
                w = 0;
            }
            line.append(word);
            w += cw;
        }
        if (line.length() > 0)
            lines.add(Component.literal(line.toString()).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<AeroSuiteFeatures.Feature> features = pageFeatures();
        int listTop = TOP_MARGIN;
        int listBottom = this.height - BOTTOM_MARGIN;
        int rowW = Math.min(420, this.width - 60);
        int rowX = (this.width - rowW) / 2;
        if (mouseX >= rowX && mouseX < rowX + rowW && mouseY >= listTop && mouseY < listBottom) {
            int i = (int) ((mouseY - listTop) / ROW_HEIGHT + scrollOffset);
            if (i >= 0 && i < features.size()) {
                String key = features.get(i).key();
                pending.put(key, !pending.getOrDefault(key, true));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
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
