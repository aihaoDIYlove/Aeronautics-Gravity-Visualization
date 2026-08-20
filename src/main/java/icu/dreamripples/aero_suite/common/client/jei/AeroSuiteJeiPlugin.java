package icu.dreamripples.aero_suite.common.client.jei;

import icu.dreamripples.aero_suite.common.AeroSuiteIds;
import icu.dreamripples.aero_suite.common.config.FeatureGates;
import icu.dreamripples.aero_suite.common.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI info 页: 为"无配方获取"的物品挂获取说明页(物品图标 + 文字, JEI 内建 INFO 类别,
 * 点击物品即可看到 "描述" 标签页). 覆盖两条事件交互链路:
 * <ul>
 *   <li>激活的末影珍珠: 主手末影珍珠 + 副手回响碎片右键(见 ActivatedEnderPearlHandler)</li>
 *   <li>瓶装星空液体: 玻璃瓶在末地之海俯视虚空右键取液(见 StarlightBottleHandler)</li>
 * </ul>
 * 门控: 功能关闭时不注册 info 页, 与 JEI 配方隐藏语义一致.
 */
@JeiPlugin
public class AeroSuiteJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AeroSuiteIds.STARLIGHT_ID, "jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (FeatureGates.isEnabled("activated_pearl")) {
            registration.addIngredientInfo(ModItems.ACTIVATED_ENDER_PEARL.get(),
                    Component.translatable("jei.starlight_logistics.info.activated_ender_pearl"));
        }
        if (FeatureGates.isEnabled("starlight_bottle")) {
            registration.addIngredientInfo(ModItems.STARLIGHT_BOTTLE.get(),
                    Component.translatable("jei.starlight_logistics.info.starlight_bottle"));
        }
    }
}
