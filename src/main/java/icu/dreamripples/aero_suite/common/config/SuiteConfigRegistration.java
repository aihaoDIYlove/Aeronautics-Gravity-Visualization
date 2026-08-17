package icu.dreamripples.aero_suite.common.config;

// import net.createmod.catnip.config.ConfigBase;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 配置注册: ConfigBase -> ModConfigSpec(仿 Aeronautics NeoForgeAeroConfigService),
 * 挂 COMMON 到 gravity_visualization。Reload 事件 -> {@link FeatureGates#invalidate()}。
 * 三个 mod 的配置按钮都指向同一份配置屏(见各入口类注册 IConfigScreenFactory)。
 */
public final class SuiteConfigRegistration {

    private SuiteConfigRegistration() {}

    public static AeroSuiteConfig register(ModContainer container, IEventBus modBus) {
        Pair<AeroSuiteConfig, ModConfigSpec> pair = (new ModConfigSpec.Builder()).configure(builder -> {
            AeroSuiteConfig config = new AeroSuiteConfig();
            config.registerAll(builder);
            return config;
        });
        AeroSuiteConfig config = pair.getLeft();
        config.specification = pair.getRight();
        FeatureGates.CONFIG = config;
        container.registerConfig(ModConfig.Type.COMMON, config.specification);

        modBus.addListener(SuiteConfigRegistration::onLoad);
        modBus.addListener(SuiteConfigRegistration::onReload);
        return config;
    }

    private static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == FeatureGates.CONFIG.specification) FeatureGates.invalidate();
    }

    private static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == FeatureGates.CONFIG.specification) FeatureGates.invalidate();
    }
}
