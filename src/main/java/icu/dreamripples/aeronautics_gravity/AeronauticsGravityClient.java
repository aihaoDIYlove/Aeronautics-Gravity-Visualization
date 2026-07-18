package icu.dreamripples.aeronautics_gravity;

import icu.dreamripples.aeronautics_gravity.client.MassVisualizer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@Mod(value = AeronauticsGravityVisualization.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AeronauticsGravityVisualization.MOD_ID, value = Dist.CLIENT)
public class AeronauticsGravityClient {

    public AeronauticsGravityClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        MassVisualizer.clientTick();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!MassVisualizer.hasActive()) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        MassVisualizer.renderOverlay(event);
    }
}
