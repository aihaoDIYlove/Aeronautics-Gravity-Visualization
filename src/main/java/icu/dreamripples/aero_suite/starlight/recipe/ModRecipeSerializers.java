package icu.dreamripples.aero_suite.starlight.recipe;

import icu.dreamripples.aero_suite.starlight.StarlightLogistics;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** 本 mod 的配方序列化器注册入口(项目首个 RECIPE_SERIALIZERS DeferredRegister)。 */
public final class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, StarlightLogistics.MOD_ID);

    public static final Supplier<RecipeSerializer<VoidTouchSmithingRecipe>> VOID_TOUCH =
            RECIPE_SERIALIZERS.register("void_touch", VoidTouchSmithingRecipe.Serializer::new);

    private ModRecipeSerializers() {}
}
