package icu.dreamripples.aero_suite.common.client;

import java.util.List;

import icu.dreamripples.aero_suite.common.AeroSuiteIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 创造栏横幅布局(静态,三段 = 三个子 mod)。
 * <p>
 * 机制:横幅本身不是物品/slot。数据层在 {@code CreativeModeTab.buildContents} 返回后
 * 往 {@code displayItems} 里塞 {@code ItemStack.EMPTY} 占位行(见
 * {@code CreativeTabBannerPaddingMixin}),渲染层({@code AeroSuiteCreativeBanner})
 * 按 {@code bannerRow - firstVisibleRow} 把贴图画到对应行上。
 * <p>
 * 行布局(ROW_SIZE=9):
 * <pre>
 * row 0: 横幅 gravity        row 1: 重力可视化 8+1 件
 * row 2: 横幅 simplification row 3: 方便物品 5+1 件
 * row 4: 横幅 starlight      row 5-6: 星空物流 11-2 件
 * </pre>
 * 物品顺序必须与 {@code ModCreativeTabs} 的 displayItems 一致——bannerRow 由
 * {@link #computeBannerRows(int[])} 按每段物品数推得,改物品数需同步这里。
 */
public final class AeroSuiteCreativeBannerLayout {
    public static final int ROW_SIZE = 9;
    public static final int FRAME_HEIGHT = 18;

    public record Section(ResourceLocation id, Component title, ResourceLocation texture, int bannerRow) {}

    /** 每段物品数,顺序 = ModCreativeTabs.displayItems 的分组顺序。 */
    private static final int[] SECTION_ITEM_COUNTS = {9, 6, 11};

    public static final List<Section> SECTIONS = build();

    private static List<Section> build() {
        return List.of(
                section("gravity", 0),
                section("simplification", 1),
                section("starlight", 2));
    }

    /**
     * 第 i 段横幅所在行 = 前面所有行数(横幅行 + 物品行)累计。
     * 横幅行不补 pad:每段物品数不足 9 的倍数时由 Mixin 补 EMPTY 到行边界,
     * 因此段间距恒为 1 行横幅 + ceil(items/9) 行物品。
     */
    private static Section section(String name, int index) {
        int row = 0;
        for (int i = 0; i < index; i++) {
            row += 1 + (SECTION_ITEM_COUNTS[i] + ROW_SIZE - 1) / ROW_SIZE; // 1 banner + full item rows
        }
        return new Section(
                ResourceLocation.fromNamespaceAndPath(AeroSuiteIds.GRAVITY_ID, "banner_" + name),
                Component.translatable("aero_suite.banner." + name),
                ResourceLocation.fromNamespaceAndPath(AeroSuiteIds.GRAVITY_ID,
                        "textures/gui/creative_tab/banner_" + name + ".png"),
                row);
    }

    /**
     * 供 Mixin 调用:前插 1 个空行(横幅 0),每段物品放完后补 pad 到 9 的倍数
     * 再插 1 个空行(下一横幅)。返回重排后的列表;搜索页列表不经过这里,不会出现空位。
     */
    public static List<net.minecraft.world.item.ItemStack> rebuildDisplayItems(
            List<net.minecraft.world.item.ItemStack> baseItems) {
        var out = new java.util.ArrayList<net.minecraft.world.item.ItemStack>(
                baseItems.size() + ROW_SIZE * (SECTION_ITEM_COUNTS.length + 1));
        addEmptyRow(out);
        int itemsLeft = SECTION_ITEM_COUNTS[0];
        int sectionIndex = 0;
        for (net.minecraft.world.item.ItemStack stack : baseItems) {
            if (itemsLeft == 0 && sectionIndex < SECTION_ITEM_COUNTS.length - 1) {
                padToRowBoundary(out);
                addEmptyRow(out);
                sectionIndex++;
                itemsLeft = SECTION_ITEM_COUNTS[sectionIndex];
            }
            out.add(stack);
            itemsLeft--;
        }
        return out;
    }

    private static void padToRowBoundary(List<net.minecraft.world.item.ItemStack> items) {
        int remainder = items.size() % ROW_SIZE;
        if (remainder == 0) return;
        for (int slot = remainder; slot < ROW_SIZE; slot++) items.add(net.minecraft.world.item.ItemStack.EMPTY);
    }

    private static void addEmptyRow(List<net.minecraft.world.item.ItemStack> items) {
        for (int slot = 0; slot < ROW_SIZE; slot++) items.add(net.minecraft.world.item.ItemStack.EMPTY);
    }

    private AeroSuiteCreativeBannerLayout() {}
}
