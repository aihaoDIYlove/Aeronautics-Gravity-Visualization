package icu.dreamripples.aero_suite.common.client.jei;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import icu.dreamripples.aero_suite.common.AeroSuiteIds;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * "神秘转化"风格 JEI 分类: 仿 Create 的 {@code MysteriousItemConversionCategory}
 * (空燃烧器→烈焰人燃烧器 / 奇异钟→幽灵钟)。Create 的版本是纯 JEI 硬编码静态列表,
 * 外部 mod 无法注入, 故自建同款分类(长箭头 + 问号 + Create 槽位贴图, 177x50 空背景)。
 *
 * <p>用途: 末影珍珠 → 激活的末影珍珠 是世界交互(主手珍珠+副手回响碎片右键)而非配方,
 * 借此分类让玩家在 JEI 查看末影珍珠的"可合成物品"时能发现激活珍珠。
 * 伪配方 {@link Conversion} 不是 vanilla Recipe, 仅承载 JEI 布局的输入/输出。
 * 门控由 {@code AeroSuiteJeiPlugin} 在注册处判断。
 */
public class PearlActivationCategory implements IRecipeCategory<PearlActivationCategory.Conversion> {

    public static final RecipeType<Conversion> TYPE =
            RecipeType.create(AeroSuiteIds.STARLIGHT_ID, "pearl_activation", Conversion.class);

    /** 关系展示用伪配方(仅供 JEI 布局, 无匹配逻辑)。 */
    public record Conversion(ItemStack input, ItemStack output) {}

    private final IDrawable icon;

    public PearlActivationCategory(IGuiHelper guiHelper, ItemStack iconStack) {
        this.icon = guiHelper.createDrawableItemStack(iconStack);
    }

    @Override
    public RecipeType<Conversion> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.starlight_logistics.pearl_activation.title");
    }

    @Override
    public int getWidth() {
        return 177;
    }

    @Override
    public int getHeight() {
        return 50;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Conversion recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 17)
                .setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
                .addItemStack(recipe.input());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 17)
                .setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
                .addItemStack(recipe.output());
    }

    @Override
    public void draw(Conversion recipe, IRecipeSlotsView iRecipeSlotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 52, 20);
        AllGuiTextures.JEI_QUESTION_MARK.render(graphics, 77, 5);
    }
}
