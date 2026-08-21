package icu.dreamripples.aero_suite.simplification.ponder;

import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * aero_suite 的思索插件,在 client setup 阶段 PonderIndex.addPlugin 注册。
 * getModId 返回 mod2(simplification_related) - 场景命名空间跟随其注册的方块。
 */
public class AeroSuitePonderPlugin implements PonderPlugin {

	@Override
	public String getModId() {
		return "simplification_related";
	}

	@Override
	public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
		ResourceLocation block = BuiltInRegistries.BLOCK.getKey(ModBlocks.CONVENIENT_ANALOG_TRANSMISSION_BLOCK.get());
		helper.forComponents(block)
			.addStoryBoard("convenient_analog_transmission/clutch",
				AnalogTransmissionPonderScenes::unpoweredClutch)
			.addStoryBoard("convenient_analog_transmission/lookup",
				AnalogTransmissionPonderScenes::redstoneLookup)
			.addStoryBoard("convenient_analog_transmission/shaft_in",
				AnalogTransmissionPonderScenes::shaftInput)
			.addStoryBoard("convenient_analog_transmission/pass_through",
				AnalogTransmissionPonderScenes::shaftInputThrough);
	}
}
