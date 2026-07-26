package icu.dreamripples.aeronautics_gravity;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.block.connected.SimpleCTBehaviour;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionVisual;
import icu.dreamripples.aeronautics_gravity.block.ModBlocks;
import icu.dreamripples.aeronautics_gravity.client.MassVisualizer;
import icu.dreamripples.aeronautics_gravity.client.ModPartialModels;
import icu.dreamripples.aeronautics_gravity.client.RedstoneCounterweightVisual;
import icu.dreamripples.aeronautics_gravity.client.RedstoneCounterweightLightVisual;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@Mod(value = AeronauticsGravityVisualization.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AeronauticsGravityVisualization.MOD_ID, value = Dist.CLIENT)
public class AeronauticsGravityClient {

    public AeronauticsGravityClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 注册 Simulated 的 AnalogTransmissionVisual 作为我们的 BlockEntity Visualizer。
        // 它内部会创建 cogInstance（侧边齿轮旋转实例），跟随 extraWheel 转速渲染齿轮转动。
        event.enqueueWork(() -> {
            ModPartialModels.init();
            SimpleBlockEntityVisualizer.builder(ModBlocks.CONVENIENT_ANALOG_TRANSMISSION_BE.get())
                    .factory(AnalogTransmissionVisual::new).apply();
            // 红石配重/配轻块灯带染色 visual(读 BlockState tier 算颜色,无 NBT sync)
            SimpleBlockEntityVisualizer.builder(ModBlocks.REDSTONE_COUNTERWEIGHT_BE.get())
                    .factory(RedstoneCounterweightVisual::new).apply();
            SimpleBlockEntityVisualizer.builder(ModBlocks.REDSTONE_COUNTERWEIGHT_LIGHT_BE.get())
                    .factory(RedstoneCounterweightLightVisual::new).apply();
        });
    }

    // 轻质玻璃 CT 连接纹理: 用 CTModel 包装 BakedModel, 相邻方向的边框在 sprite sheet 子格层面消失。
    // 不走 CreateRegistrate(它 extends Registrate, Registrate 是 Create 的 jarjar, 不在 compile classpath),
    // 直接监听 ModelEvent.ModifyBakingResult 自己包装, 等价于 Create 内部的 registerCTBehviour。
    @SubscribeEvent
    public static void onModifyBaking(ModelEvent.ModifyBakingResult event) {
        ResourceLocation blockId = ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", "lightweight_glass");
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == Blocks.AIR) return;
        // original=lightweight_glass(16x16 单帧, model 用), target=lightweight_glass_connected(128x128 OMNIDIRECTIONAL sprite sheet)
        // OMNIDIRECTIONAL 看 4 正方向 + 4 对角: 区分"十字中心"(4对角不连->补4角像素)和"大片内部"(4对角连->全透明), 解决 CROSS 的内角缺像素问题。
        CTSpriteShiftEntry shift = CTSpriteShifter.getCT(
                AllCTTypes.OMNIDIRECTIONAL,
                ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", "block/lightweight_glass"),
                ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", "block/lightweight_glass_connected"));
        ConnectedTextureBehaviour behaviour = new SimpleCTBehaviour(shift);
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation mrl = BlockModelShaper.stateToModelLocation(blockId, state);
            BakedModel original = event.getModels().get(mrl);
            if (original != null) {
                event.getModels().put(mrl, new CTModel(original, behaviour));
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        MassVisualizer.clientTick();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!MassVisualizer.hasActive()) return;
        var stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                && stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        MassVisualizer.renderOverlay(event);
    }
}
