package icu.dreamripples.aero_suite.starlight.event;

import icu.dreamripples.aero_suite.common.config.FeatureGates;
import icu.dreamripples.aero_suite.common.registry.ModItems;
import icu.dreamripples.aero_suite.starlight.StarlightLogistics;
// import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * 星空酿造基底: 瓶装星空液体在酿造台底部槽位替代粗制药水(Awkward Potion)作为基底,
 * 直接加主材料酿出成品药水, 省去"水瓶 + 下界疣"的种植环节.
 * <p>
 * 实现: 委托式 {@link net.neoforged.neoforge.common.brewing.IBrewingRecipe} --
 * 构造一个携带 {@code POTION_CONTENTS=awkward} 的虚拟粗制药水栈, 把
 * {@code isIngredient}/{@code getOutput} 全部委托给 vanilla 的
 * {@link PotionBrewing#hasPotionMix}/{@link PotionBrewing#mix},
 * 由原版混合表回答"能不能酿/酿出什么"(vanilla 加新药水时本配方自动跟进, 零维护).
 * 输出固定为可饮用药水; 喷溅/滞留仍走火药/龙息二次加工(vanilla container mix).
 * <p>
 * 参数顺序(对照 BrewingStandBlockEntity 调用点核实): {@code doBrew} 调
 * {@code mix(reagent, potionStack)}, {@code hasPotionMix} 从第一参读药水组件、第二参测材料 --
 * 故委托调用为 {@code mix(reagent, AWKWARD)} 与 {@code hasPotionMix(AWKWARD, reagent)}.
 * <p>
 * 双侧注意: 本事件客户端也触发(用于菜单槽位校验/JEI), 客户端
 * {@code ServerLifecycleHooks.getCurrentServer()} 为 null --
 * {@link #brewing()} 按侧分发: 服务端(含单机内建服)取 MinecraftServer,
 * 客户端经独立内部类隔离 Minecraft 引用(避免专用服类加载 client 类).
 * <p>
 * 门控: 酿造配方是代码注册非 JSON, FeatureEnabledCondition 不适用 --
 * 在 isInput/getOutput 内实时查 {@link FeatureGates#isEnabled}(关闭后瓶子本身也会被
 * DisabledItemCollector 获取即删, 双保险).
 */
@EventBusSubscriber(modid = StarlightLogistics.MOD_ID)
public class StarlightBrewingHandler {

    /** 虚拟粗制药水基底: 仅作 POTION_CONTENTS 载体参与委托查询, 永不出现在输出中. */
    private static final ItemStack AWKWARD_BASE = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);

    private static PotionBrewing brewing() {
        if (FMLEnvironment.dist.isClient()) {
            return ClientOnly.brewing();
        }
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.potionBrewing() : PotionBrewing.EMPTY;
    }

    /** 客户端类隔离: 本类只在 dist==client 时被触及. */
    private static final class ClientOnly {
        static PotionBrewing brewing() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            return mc != null && mc.level != null ? mc.level.potionBrewing() : PotionBrewing.EMPTY;
        }
    }

    @SubscribeEvent
    public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addRecipe(new net.neoforged.neoforge.common.brewing.IBrewingRecipe() {
            @Override
            public boolean isInput(ItemStack stack) {
                return FeatureGates.isEnabled("recipe_starlight_brewing")
                        && stack.is(ModItems.STARLIGHT_BOTTLE.get());
            }

            @Override
            public boolean isIngredient(ItemStack reagent) {
                if (!FeatureGates.isEnabled("recipe_starlight_brewing")) return false;
                return brewing().hasPotionMix(AWKWARD_BASE, reagent);
            }

            @Override
            public ItemStack getOutput(ItemStack input, ItemStack reagent) {
                if (!isInput(input) || !isIngredient(reagent)) return ItemStack.EMPTY;
                return brewing().mix(reagent, AWKWARD_BASE);
            }
        });
    }
}
