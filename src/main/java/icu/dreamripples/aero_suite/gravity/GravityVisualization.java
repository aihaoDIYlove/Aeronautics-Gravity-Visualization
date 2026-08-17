package icu.dreamripples.aero_suite.gravity;

import icu.dreamripples.aero_suite.common.AeroSuite;
import icu.dreamripples.aero_suite.common.AeroSuiteIds;
import icu.dreamripples.aero_suite.common.config.FeatureEnabledCondition;
import icu.dreamripples.aero_suite.common.config.SuiteConfigRegistration;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import icu.dreamripples.aero_suite.common.registry.ModCreativeTabs;
import icu.dreamripples.aero_suite.common.registry.ModItems;
import icu.dreamripples.aero_suite.gravity.advancement.ModTriggers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * mod1 入口: 航空学：重力可视化(gravity_visualization)。
 * 持有火花魔杖/便携图解/配重配轻系列的注册 + 成就触发器 + 共享创造页。
 */
@Mod(GravityVisualization.MOD_ID)
public class GravityVisualization {
    public static final String MOD_ID = AeroSuiteIds.GRAVITY_ID;

    public GravityVisualization(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        registerConfigScreen(container);
        // COMMON 配置挂 mod1 名下(条件类型 + 配置屏也归它), 三个 mod 共享
        SuiteConfigRegistration.register(container, modEventBus);
        modBusCondition(modEventBus);
        ModItems.GRAVITY_ITEMS.register(modEventBus);
        ModBlocks.GRAVITY_BLOCKS.register(modEventBus);
        ModBlocks.GRAVITY_BLOCK_ENTITIES.register(modEventBus);
        ModTriggers.TRIGGERS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        AeroSuite.LOGGER.info("Aeronautics: Gravity Visualization loaded!");
    }

    // CONDITION_CODECS registry 存 MapCodec<? extends ICondition>(serializer)。
    // DeferredRegister.create 的泛型推断在这里不通过, 改用 RegisterEvent 手动注入。
    private static void modBusCondition(IEventBus modEventBus) {
        // lambda 参数显式写成 RegisterEvent，避免编译器把签名擦除成基类 net.neoforged.bus.api.Event
        // (mod event bus 只接受 IModBusEvent 子类，擦除后会在运行期被拒收并抛 IllegalArgumentException)。
        modEventBus.addListener((net.neoforged.neoforge.registries.RegisterEvent event) -> {
            if (event.getRegistryKey() == net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.CONDITION_CODECS) {
                event.register(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.CONDITION_CODECS,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "feature_enabled"),
                        () -> FeatureEnabledCondition.CODEC);
            }
        });
    }

    // 三个 mod 的配置按钮都指向同一份配置屏(catnip BaseConfigScreen, 读 gravity_visualization 的 COMMON 配置)
    private static void registerConfigScreen(net.neoforged.fml.ModContainer container) {
        container.registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (c, last) -> new net.createmod.catnip.config.ui.BaseConfigScreen(last, "gravity_visualization"));
    }
}
