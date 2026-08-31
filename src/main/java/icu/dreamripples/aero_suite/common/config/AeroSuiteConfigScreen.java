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
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Aeronautics Suite 自绘配置屏(替代 Catnip BaseConfigScreen -- 后者不读 lang 文件无法本地化)。
 *
 * <p>三级菜单 + 即时保存:
 * <ul>
 *   <li><b>一级(分类)</b>: 物品配方相关 / 数值特性相关 + 重置所有设置 + 返回至模组页。</li>
 *   <li><b>二级(物品配方相关)</b>: 三个分组入口(航空学:重力可视化/方便物品/星空物流)。</li>
 *   <li><b>三级(物品配方相关)</b>: 标题 + "启用/禁用全部"主开关行 + 手写滚动列表(图标/标签/开关行,
 *       点击即生效)。</li>
 *   <li><b>二级(数值特性相关)</b>: 特性开关行(现仅机械手载具抓取)+ 数值编辑行 -- 左键 +step /
 *       右键 -step / 潜行点击 ×10, 现为机械手抓取的 饥饿/耐久/背罐气 三个每秒消耗值。</li>
 *   <li>各层底部唯一的"返回至上级菜单"按钮。</li>
 * </ul>
 *
 * <p>开关改动即时落盘({@code ConfigBool.set})并 {@link FeatureGates#invalidate()} 重建缓存;
 * 纯配方开关(产物为 vanilla/其他 mod)改动后提示一次需 {@code /reload} --
 * {@link FeatureEnabledCondition} 在数据包加载时求值。数值改动经 {@code ConfigFloat/Int.set}
 * 同样即时落盘, 读取方(ExtendoGrabServer)实时 get, 下一结算 tick 即生效。
 * 配置屏只在客户端打开, 多人下改动只写本侧文件 -- 与原 Catnip 屏一致。
 */
public class AeroSuiteConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int TOP_MARGIN = 64;
    private static final int BOTTOM_MARGIN = 44;

    private enum Mode { MAIN, GROUPS, FEATURES, TUNABLES }

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
        switch (mode) {
            case MAIN -> initMain();
            case GROUPS -> initGroups();
            case TUNABLES -> initTunables();
            case FEATURES -> initFeatures();
        }
    }

    private Button backButton(Runnable action) {
        return Button.builder(Component.translatable("aero_suite.config.back"), b -> action.run())
                .bounds((this.width - 200) / 2, this.height - 28, 200, 20).build();
    }

    // ── 一级菜单(分类) ────────────────────────────────────────

    private void initMain() {
        int bw = 260, bh = 22, gap = 8;
        int y = 60;
        addRenderableWidget(Button.builder(Component.translatable("aero_suite.config.category.items"), b -> {
            mode = Mode.GROUPS;
            init();
        }).bounds((this.width - bw) / 2, y, bw, bh).build());
        y += bh + gap;
        addRenderableWidget(Button.builder(Component.translatable("aero_suite.config.category.tunables"), b -> {
            mode = Mode.TUNABLES;
            init();
        }).bounds((this.width - bw) / 2, y, bw, bh).build());
        y += bh + gap * 2;
        addRenderableWidget(Button.builder(Component.translatable("aero_suite.config.reset_all"), b -> {
            for (AeroSuiteFeatures.Feature f : AeroSuiteFeatures.ALL)
                setKey(f, f.defaultValue());
            resetTunables();
            scrollOffset = 0;
        }).bounds((this.width - bw) / 2, y, bw, bh).build());
        y += bh + gap;
        addRenderableWidget(Button.builder(Component.translatable("aero_suite.config.back_to_mods"),
                b -> onClose()).bounds((this.width - bw) / 2, y, bw, bh).build());
    }

    // ── 二级菜单(物品配方相关: 三个分组入口) ───────────────────

    private void initGroups() {
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
        addRenderableWidget(backButton(() -> {
            mode = Mode.MAIN;
            init();
        }));
    }

    // ── 三级菜单(开关列表) ────────────────────────────────────

    private void initFeatures() {
        addRenderableWidget(backButton(() -> {
            mode = Mode.GROUPS;
            init();
        }));
    }

    // ── 二级菜单(数值特性相关) ────────────────────────────────

    private void initTunables() {
        addRenderableWidget(backButton(() -> {
            mode = Mode.MAIN;
            init();
        }));
    }

    /** 数值编辑行描述: 标签/范围/步长/读写器(读写器内部自判 CONFIG null)。 */
    private record NumRow(String labelKey, String tooltipKey, double step, double min, double max,
                          boolean integer, DoubleSupplier getter, DoubleConsumer setter) {

        String format(double v) {
            return integer ? String.valueOf((long) v) : String.format("%.1f", v);
        }
    }

    private static AeroSuiteConfig config() {
        return FeatureGates.CONFIG;
    }

    /** 数值页头部开关行: 机械手载具抓取(其数值行紧随其后)。 */
    private List<AeroSuiteFeatures.Feature> headGateRows() {
        List<AeroSuiteFeatures.Feature> list = new ArrayList<>();
        for (AeroSuiteFeatures.Feature f : AeroSuiteFeatures.ALL)
            if ("extendo_grab".equals(f.key())) list.add(f);
        return list;
    }

    /** 数值页尾部开关行: 与数值无关的独立行为开关(星空酿造基底), 排在所有数值行之后。 */
    private List<AeroSuiteFeatures.Feature> tailGateRows() {
        List<AeroSuiteFeatures.Feature> list = new ArrayList<>();
        for (AeroSuiteFeatures.Feature f : AeroSuiteFeatures.ALL)
            if ("recipe_starlight_brewing".equals(f.key())) list.add(f);
        return list;
    }

    /** 数值特性行(顺序即显示顺序; 现仅机械手抓取的三个每秒消耗)。 */
    private List<NumRow> tunableRows() {
        List<NumRow> rows = new ArrayList<>();
        rows.add(new NumRow("aero_suite.tunables.hunger", "aero_suite.tunables.hunger.tooltip",
                0.5, 0, 20, false,
                () -> { var c = config(); return c != null ? c.tunables.extendoGrabHunger.getF() : 0; },
                v -> { var c = config(); if (c != null) c.tunables.extendoGrabHunger.set(v); }));
        rows.add(new NumRow("aero_suite.tunables.durability", "aero_suite.tunables.durability.tooltip",
                1, 0, 100, true,
                () -> { var c = config(); return c != null ? c.tunables.extendoGrabDurability.get() : 0; },
                v -> { var c = config(); if (c != null) c.tunables.extendoGrabDurability.set((int) v); }));
        rows.add(new NumRow("aero_suite.tunables.air", "aero_suite.tunables.air.tooltip",
                1, 1, 900, true,
                () -> { var c = config(); return c != null ? c.tunables.extendoGrabAir.get() : 0; },
                v -> { var c = config(); if (c != null) c.tunables.extendoGrabAir.set((int) v); }));
        return rows;
    }

    /** 重置所有数值特性(默认值单一事实源 = {@link AeroSuiteConfig.Tunables} 常量)。 */
    private void resetTunables() {
        AeroSuiteConfig c = config();
        if (c == null) return;
        c.tunables.extendoGrabHunger.set((double) AeroSuiteConfig.Tunables.HUNGER_DEFAULT);
        c.tunables.extendoGrabDurability.set(AeroSuiteConfig.Tunables.DURABILITY_DEFAULT);
        c.tunables.extendoGrabAir.set(AeroSuiteConfig.Tunables.AIR_DEFAULT);
    }

    private List<AeroSuiteFeatures.Feature> pageFeatures() {
        List<AeroSuiteFeatures.Feature> list = new ArrayList<>();
        for (AeroSuiteFeatures.Feature f : AeroSuiteFeatures.ALL)
            if (f.group() == page && !isTunablesOwned(f)) list.add(f);
        return list;
    }

    /** 开关展示在"数值特性相关"页的特性, 物品配方分组列表不再重复出现。 */
    private static boolean isTunablesOwned(AeroSuiteFeatures.Feature f) {
        // extendo_grab: 数值页有对应的三个消耗值编辑行, 开关行与之合并
        // recipe_starlight_brewing: 纯行为开关(星空酿造基底), 归入数值页集中管理
        return "extendo_grab".equals(f.key()) || "recipe_starlight_brewing".equals(f.key());
    }

    /** 即时保存: 写 toml + 重建门控缓存; 纯配方开关改动提示一次 /reload。 */
    private void setKey(AeroSuiteFeatures.Feature f, boolean value) {
        if (FeatureGates.CONFIG == null || FeatureGates.isEnabled(f.key()) == value)
            return;
        FeatureGates.CONFIG.byKey.get(f.key()).set(value);
        FeatureGates.invalidate();
        if (isRecipeGate(f) && !reloadHintShown) {
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
        switch (mode) {
            case MAIN -> graphics.drawCenteredString(this.font, this.title, this.width / 2, 24, 0xFFFFFF);
            case GROUPS -> graphics.drawCenteredString(this.font,
                    Component.translatable("aero_suite.config.category.items"), this.width / 2, 24, 0xFFFFFF);
            case FEATURES -> renderFeatures(graphics, mouseX, mouseY);
            case TUNABLES -> renderTunables(graphics, mouseX, mouseY);
        }
    }

    private void renderFeatures(GuiGraphics graphics, int mouseX, int mouseY) {
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
            lines.add(Component.translatable(
                    f.deletesItems() ? "aero_suite.config.item_gate"
                            : isRecipeGate(f) ? "aero_suite.config.recipe_gate"
                            : "aero_suite.config.behavior_gate")
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal(f.key()).withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    private void renderTunables(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(this.font,
                Component.translatable("aero_suite.config.category.tunables"), this.width / 2, 12, 0xFFFFFF);

        int rowW = Math.min(420, this.width - 60);
        int rowX = (this.width - rowW) / 2;
        AeroSuiteConfig c = config();
        if (c == null) {
            graphics.drawCenteredString(this.font, "config not loaded", this.width / 2, 60, 0xFF5555);
            return;
        }

        int listTop = TOP_MARGIN;
        List<AeroSuiteFeatures.Feature> head = headGateRows();
        List<AeroSuiteFeatures.Feature> tail = tailGateRows();
        List<NumRow> rows = tunableRows();

        // 行布局: 机械手开关 -> 机械手数值行 x3 -> 独立行为开关(星空酿造基底)
        int y;
        boolean hovered;
        for (int i = 0; i < head.size(); i++) {
            AeroSuiteFeatures.Feature f = head.get(i);
            y = listTop + i * ROW_HEIGHT;
            hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            graphics.fill(rowX, y, rowX + rowW, y + ROW_HEIGHT - 2, hovered ? 0x60FFFFFF : 0x30000000);
            Item icon = f.icon().get();
            if (icon != null)
                graphics.renderItem(new ItemStack(icon), rowX + 4, y + 4);
            graphics.drawString(this.font, Component.translatable("aero_suite.feature." + f.key()),
                    rowX + 28, y + 8, 0xFFFFFF);
            drawState(graphics, rowX, rowW, y, FeatureGates.isEnabled(f.key()));
        }

        for (int i = 0; i < rows.size(); i++) {
            NumRow row = rows.get(i);
            y = listTop + (head.size() + i) * ROW_HEIGHT;
            hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            graphics.fill(rowX, y, rowX + rowW, y + ROW_HEIGHT - 2, hovered ? 0x60FFFFFF : 0x30000000);
            graphics.drawString(this.font, Component.translatable(row.labelKey()), rowX + 28, y + 8, 0xFFFFFF);
            String value = row.format(row.getter().getAsDouble());
            graphics.drawString(this.font, value, rowX + rowW - 16 - this.font.width(value), y + 8, 0x55FF55);
        }

        for (int i = 0; i < tail.size(); i++) {
            AeroSuiteFeatures.Feature f = tail.get(i);
            y = listTop + (head.size() + rows.size() + i) * ROW_HEIGHT;
            hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            graphics.fill(rowX, y, rowX + rowW, y + ROW_HEIGHT - 2, hovered ? 0x60FFFFFF : 0x30000000);
            Item icon = f.icon().get();
            if (icon != null)
                graphics.renderItem(new ItemStack(icon), rowX + 4, y + 4);
            graphics.drawString(this.font, Component.translatable("aero_suite.feature." + f.key()),
                    rowX + 28, y + 8, 0xFFFFFF);
            drawState(graphics, rowX, rowW, y, FeatureGates.isEnabled(f.key()));
        }

        // tooltip(悬浮行)
        hoveredIndex = -1;
        int totalRows = head.size() + rows.size() + tail.size();
        if (mouseX >= rowX && mouseX < rowX + rowW && mouseY >= listTop
                && mouseY < listTop + totalRows * ROW_HEIGHT) {
            int idx = (int) ((mouseY - listTop) / ROW_HEIGHT);
            hoveredIndex = idx;
            List<Component> lines = new ArrayList<>();
            if (idx < head.size()) {
                AeroSuiteFeatures.Feature f = head.get(idx);
                lines.add(Component.translatable("aero_suite.feature." + f.key()).withStyle(ChatFormatting.GOLD));
                lines.addAll(wrap(Component.translatable("aero_suite.feature." + f.key() + ".desc").getString(), 240));
                lines.add(Component.translatable(f.deletesItems() ? "aero_suite.config.item_gate"
                        : isRecipeGate(f) ? "aero_suite.config.recipe_gate"
                        : "aero_suite.config.behavior_gate").withStyle(ChatFormatting.GRAY));
            } else if (idx - head.size() < rows.size()) {
                NumRow row = rows.get(idx - head.size());
                lines.add(Component.translatable(row.labelKey()).withStyle(ChatFormatting.GOLD));
                lines.addAll(wrap(Component.translatable(row.tooltipKey()).getString(), 240));
                lines.add(Component.translatable("aero_suite.config.edit_hint").withStyle(ChatFormatting.GRAY));
            } else if (idx - head.size() - rows.size() < tail.size()) {
                AeroSuiteFeatures.Feature f = tail.get(idx - head.size() - rows.size());
                lines.add(Component.translatable("aero_suite.feature." + f.key()).withStyle(ChatFormatting.GOLD));
                lines.addAll(wrap(Component.translatable("aero_suite.feature." + f.key() + ".desc").getString(), 240));
                lines.add(Component.translatable(f.deletesItems() ? "aero_suite.config.item_gate"
                        : isRecipeGate(f) ? "aero_suite.config.recipe_gate"
                        : "aero_suite.config.behavior_gate").withStyle(ChatFormatting.GRAY));
            }
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    /** 纯配方开关: 不删物品且 key 以 recipe_ 开头(产物为 vanilla/其他 mod, 改动需 /reload)。 */
    private static boolean isRecipeGate(AeroSuiteFeatures.Feature f) {
        return !f.deletesItems() && f.key().startsWith("recipe_");
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

        if (mode == Mode.FEATURES) {
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

        if (mode == Mode.TUNABLES) {
            int listTop = TOP_MARGIN;
            if (mouseX >= rowX && mouseX < rowX + rowW && mouseY >= listTop) {
                int idx = (int) ((mouseY - listTop) / ROW_HEIGHT);
                List<AeroSuiteFeatures.Feature> head = headGateRows();
                List<AeroSuiteFeatures.Feature> tail = tailGateRows();
                List<NumRow> rows = tunableRows();
                // 与渲染行序一致: 头部开关 -> 数值行 -> 尾部开关
                if (idx < head.size()) {
                    AeroSuiteFeatures.Feature f = head.get(idx);
                    setKey(f, !FeatureGates.isEnabled(f.key()));
                    return true;
                }
                int numIdx = idx - head.size();
                if (numIdx >= 0 && numIdx < rows.size()) {
                    NumRow row = rows.get(numIdx);
                    double step = row.step() * (hasShiftDown() ? 10 : 1);
                    double sign = button == 0 ? 1 : button == 1 ? -1 : 0;
                    if (sign != 0) {
                        row.setter().accept(Mth.clamp(row.getter().getAsDouble() + sign * step, row.min(), row.max()));
                        return true;
                    }
                }
                int tailIdx = idx - head.size() - rows.size();
                if (tailIdx >= 0 && tailIdx < tail.size()) {
                    AeroSuiteFeatures.Feature f = tail.get(tailIdx);
                    setKey(f, !FeatureGates.isEnabled(f.key()));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && mode == Mode.FEATURES) {
            List<AeroSuiteFeatures.Feature> features = pageFeatures();
            int visibleRows = (this.height - BOTTOM_MARGIN - TOP_MARGIN) / ROW_HEIGHT;
            // 双向钳制(短列表上界为 0),并吸附到整数防止小数 offset 让渲染行/点击判定错位
            int maxOffset = Math.max(0, features.size() - visibleRows);
            scrollOffset = Math.round(Mth.clamp(scrollOffset - scrollY, 0, maxOffset));
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
