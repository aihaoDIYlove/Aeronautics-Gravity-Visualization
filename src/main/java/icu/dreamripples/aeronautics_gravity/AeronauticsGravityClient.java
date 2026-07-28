package icu.dreamripples.aeronautics_gravity;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.block.connected.SimpleCTBehaviour;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionVisual;
import icu.dreamripples.aeronautics_gravity.block.ModBlocks;
import icu.dreamripples.aeronautics_gravity.item.ModItems;
import icu.dreamripples.aeronautics_gravity.client.MassVisualizer;
import icu.dreamripples.aeronautics_gravity.client.ModPartialModels;
import icu.dreamripples.aeronautics_gravity.client.RedstoneCounterweightVisual;
import icu.dreamripples.aeronautics_gravity.client.RedstoneCounterweightLightVisual;
import icu.dreamripples.aeronautics_gravity.client.StabilizerRenderer;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
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
        // StabilizerRenderer 同时画灯带(染色)和 portal 星空。不用 Flywheel Visual:
        // Flywheel 的 SectionCompilerMixin 对有 Visualizer 的 BE cancel renderable,BER.render 不被调。
        // BER 注册同步调用(不 enqueueWork),确保在 BlockEntityRenderDispatcher 构造前注册到 BY_TYPE map。
        BlockEntityRenderers.register(ModBlocks.STABILIZER_BE.get(), StabilizerRenderer::new);
        event.enqueueWork(() -> {
            ModPartialModels.init();
            SimpleBlockEntityVisualizer.builder(ModBlocks.CONVENIENT_ANALOG_TRANSMISSION_BE.get())
                    .factory(AnalogTransmissionVisual::new).apply();
            // 红石配重/配轻块灯带染色 visual(读 BlockState tier 算颜色,无 NBT sync)
            SimpleBlockEntityVisualizer.builder(ModBlocks.REDSTONE_COUNTERWEIGHT_BE.get())
                    .factory(RedstoneCounterweightVisual::new).apply();
            SimpleBlockEntityVisualizer.builder(ModBlocks.REDSTONE_COUNTERWEIGHT_LIGHT_BE.get())
                    .factory(RedstoneCounterweightLightVisual::new).apply();
            // 手持火花魔杖时获得护目镜视野: Create 的 GogglesItem 用一组 Predicate<Player> 判定
            // "是否佩戴护目镜",默认只查 HEAD 槽。addIsWearingPredicate 是官方为"手持替代品"预留的
            // 扩展点(GogglesItem Javadoc 原文)。注册后 3 处调用点同时生效: 准星瞄准方块的 overlay
            // (GoggleOverlayRenderer)、kinetic 物品 tooltip 应力数值(KineticStats)、旋转方向粒子。
            GogglesItem.addIsWearingPredicate(player ->
                    player.getMainHandItem().is(ModItems.SPARK_WAND.get())
                            || player.getOffhandItem().is(ModItems.SPARK_WAND.get()));
        });
    }

    // 轻质玻璃 CT 连接纹理: 用 CTModel 包装 BakedModel, 相邻方向的边框在 sprite sheet 子格层面消失。
    // 不走 CreateRegistrate(它 extends Registrate, Registrate 是 Create 的 jarjar, 不在 compile classpath),
    // 直接监听 ModelEvent.ModifyBakingResult 自己包装, 等价于 Create 内部的 registerCTBehviour。
    // 给玻璃方块注册 CT 连接纹理: 用 CTModel 包装 BakedModel, 相邻方向的边框在 sprite sheet 子格层面消失。
    // original=block/<name>.png(单帧, model 用), target=block/<name>_connected.png(8x8 OMNIDIRECTIONAL sprite sheet, 64格)。
    // 不走 CreateRegistrate(它 extends Registrate, Registrate 是 Create 的 jarjar, 不在 compile classpath),
    // 直接监听 ModelEvent.ModifyBakingResult 自己包装, 等价于 Create 内部的 registerCTBehviour。
    @SubscribeEvent
    public static void onModifyBaking(ModelEvent.ModifyBakingResult event) {
        registerGlassCT(event, "lightweight_glass");
        registerGlassCT(event, "ultralight_glass");
    }

    private static void registerGlassCT(ModelEvent.ModifyBakingResult event, String name) {
        ResourceLocation blockId = ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", name);
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == Blocks.AIR) return;
        CTSpriteShiftEntry shift = CTSpriteShifter.getCT(
                AllCTTypes.OMNIDIRECTIONAL,
                ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", "block/" + name),
                ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", "block/" + name + "_connected"));
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
