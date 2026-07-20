package icu.dreamripples.aeronautics_gravity;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionVisual;
import icu.dreamripples.aeronautics_gravity.block.ModBlocks;
import icu.dreamripples.aeronautics_gravity.client.MassVisualizer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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
        event.enqueueWork(() -> SimpleBlockEntityVisualizer
                .builder(ModBlocks.CONVENIENT_ANALOG_TRANSMISSION_BE.get())
                .factory(AnalogTransmissionVisual::new)
                .apply());
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
