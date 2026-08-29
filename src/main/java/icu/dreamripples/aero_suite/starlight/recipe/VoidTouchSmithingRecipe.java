package icu.dreamripples.aero_suite.starlight.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import icu.dreamripples.aero_suite.starlight.component.ModDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

import java.util.stream.Stream;

/**
 * 虚空之触锻造升级: 任意镐子 + 虚空之触锻造模板 + 回响碎片 -> 原镐子原样保留(附魔/耐久/
 * 第三方组件全部继承), 仅注入 {@link ModDataComponents#VOID_TOUCH} 标记。
 * <p>
 * 不用 vanilla {@code smithing_transform}: 它的 result 是固定 ItemStack(transmuteCopy 换物品),
 * 表达不了"base 原样返回 + 注入组件", 也因此无法兼容任意 mod 的镐子。
 * 三槽匹配逻辑复用父类; JSON 里仍须写 result(仅用于 JEI/配方书展示结果图标, 不参与产出)。
 * <p>
 * 注意: 父类字段是包私有, 跨包子类不可见 -> 本类自持一份。
 */
public class VoidTouchSmithingRecipe extends SmithingTransformRecipe {

    final Ingredient template;
    final Ingredient base;
    final Ingredient addition;
    final ItemStack result;

    public VoidTouchSmithingRecipe(Ingredient template, Ingredient base, Ingredient addition, ItemStack result) {
        super(template, base, addition, result);
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        ItemStack result = input.base().copy();
        result.set(ModDataComponents.VOID_TOUCH.get(), Unit.INSTANCE);
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.VOID_TOUCH.get();
    }

    @Override
    public boolean isIncomplete() {
        // result 仅作展示, 不计入"未完成"
        return Stream.of(template, base, addition).anyMatch(Ingredient::hasNoItems);
    }

    public static class Serializer implements RecipeSerializer<VoidTouchSmithingRecipe> {
        private static final MapCodec<VoidTouchSmithingRecipe> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Ingredient.CODEC.fieldOf("template").forGetter(r -> r.template),
                        Ingredient.CODEC.fieldOf("base").forGetter(r -> r.base),
                        Ingredient.CODEC.fieldOf("addition").forGetter(r -> r.addition),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result)
                ).apply(instance, VoidTouchSmithingRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, VoidTouchSmithingRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<VoidTouchSmithingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, VoidTouchSmithingRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static VoidTouchSmithingRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            Ingredient template = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            Ingredient base = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            Ingredient addition = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            return new VoidTouchSmithingRecipe(template, base, addition, result);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, VoidTouchSmithingRecipe recipe) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.template);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.base);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.addition);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
        }
    }
}
