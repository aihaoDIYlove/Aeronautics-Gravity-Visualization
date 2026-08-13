package icu.dreamripples.aeronautics_gravity;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.content.fluids.PipeAttachmentModel;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.contraptions.pulley.HosePulleyVisual;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.block.connected.SimpleCTBehaviour;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionVisual;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineRenderer;
import icu.dreamripples.aeronautics_gravity.block.GlowSignBlock;
import icu.dreamripples.aeronautics_gravity.block.GlowSignBlockEntity;
import icu.dreamripples.aeronautics_gravity.block.ModBlocks;
import icu.dreamripples.aeronautics_gravity.network.GlowSignScrollPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import icu.dreamripples.aeronautics_gravity.item.ModItems;
import icu.dreamripples.aeronautics_gravity.client.MassVisualizer;
import icu.dreamripples.aeronautics_gravity.client.ModPartialModels;
import icu.dreamripples.aeronautics_gravity.client.RedstoneCounterweightVisual;
import icu.dreamripples.aeronautics_gravity.client.RedstoneCounterweightLightVisual;
import icu.dreamripples.aeronautics_gravity.client.StabilizerRenderer;
import icu.dreamripples.aeronautics_gravity.client.GlowSignRenderer;
import icu.dreamripples.aeronautics_gravity.client.WorldAnchorRenderer;
import net.minecraft.client.renderer.Sheets;
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
        // StabilizerRenderer 画灯带(染色,换皮后用 72 突出 REDSTONE_INDICATOR)。不用 Flywheel Visual:
        // Flywheel SectionCompilerMixin 对有 Visualizer 的 BE cancel renderable,BER.render 不被调。
        // BER 同步注册(不 enqueueWork),确保在 BlockEntityRenderDispatcher 构造前注册到 BY_TYPE map。
        // WorldAnchorRenderer 画灯带(ANCHOR_INDICATOR 染色) + portal 星空(从 stabilizer 迁移)。
        BlockEntityRenderers.register(ModBlocks.STABILIZER_BE.get(), StabilizerRenderer::new);
        BlockEntityRenderers.register(ModBlocks.WORLD_ANCHOR_BE.get(), WorldAnchorRenderer::new);
        // 变速式便携引擎:复用 Simulated 的 PortableEngineRenderer(BE 继承 PortableEngineBlockEntity,多态成立)
        BlockEntityRenderers.register(ModBlocks.VARIABLE_SPEED_PORTABLE_ENGINE_BE.get(), PortableEngineRenderer::new);
        // 寻址牌:自定义 BER(歌词式 4 行,不画木牌,只画文字)
        BlockEntityRenderers.register(ModBlocks.GLOW_SIGN_BE.get(), GlowSignRenderer::new);
        event.enqueueWork(() -> {
            // 注册自定义 WoodType 到 Sheets(SIGN_MATERIALS 是静态收集,后注册的 WoodType 需手动补登记)
            Sheets.addWoodType(AeronauticsGravityVisualization.GLOW_SIGN_WOOD_TYPE);
            ModPartialModels.init();
            SimpleBlockEntityVisualizer.builder(ModBlocks.CONVENIENT_ANALOG_TRANSMISSION_BE.get())
                    .factory(AnalogTransmissionVisual::new).apply();
            // 红石配重/配轻块灯带染色 visual(读 BlockState tier 算颜色,无 NBT sync)
            SimpleBlockEntityVisualizer.builder(ModBlocks.REDSTONE_COUNTERWEIGHT_BE.get())
                    .factory(RedstoneCounterweightVisual::new).apply();
            SimpleBlockEntityVisualizer.builder(ModBlocks.REDSTONE_COUNTERWEIGHT_LIGHT_BE.get())
                    .factory(RedstoneCounterweightLightVisual::new).apply();
            // 虚空软管滑轮: 复用 Create 的 HosePulleyVisual(Flywheel Visualizer). BER(AbstractPulleyRenderer)
            // 在 Flywheel 环境下 renderSafe 首行 if(supportsVisualization) return 直接跳过, 软管/线圈/磁铁
            // 全由 Visualizer 渲染 -- 所以必须注册 Visualizer, 不能只注册 BER(只注册 BER = 只剩 blockstate 外壳).
            SimpleBlockEntityVisualizer.builder(ModBlocks.VOID_HOSE_PULLEY_BE.get())
                    .factory(HosePulleyVisual::new).apply();
            // 手持火花魔杖时获得护目镜视野: Create 的 GogglesItem 用一组 Predicate<Player> 判定
            // "是否佩戴护目镜",默认只查 HEAD 槽。addIsWearingPredicate 是官方为"手持替代品"预留的
            // 扩展点(GogglesItem Javadoc 原文)。注册后 3 处调用点同时生效: 准星瞄准方块的 overlay
            // (GoggleOverlayRenderer)、kinetic 物品 tooltip 应力数值(KineticStats)、旋转方向粒子。
            GogglesItem.addIsWearingPredicate(player ->
                    player.getMainHandItem().is(ModItems.SPARK_WAND.get())
                            || player.getOffhandItem().is(ModItems.SPARK_WAND.get()));
        });
    }

    // 给玻璃方块注册 CT 连接纹理: 用 CTModel 包装 BakedModel, 相邻方向的边框在 sprite sheet 子格层面消失。
    // original=block/<name>.png(单帧, model 用), target=block/<name>_connected.png(8x8 OMNIDIRECTIONAL sprite sheet, 64格)。
    // 不走 CreateRegistrate(它 extends Registrate, Registrate 是 Create 的 jarjar, 不在 compile classpath),
    // 直接监听 ModelEvent.ModifyBakingResult 自己包装, 等价于 Create 内部的 CreateRegistrate.connectedTextures。
    @SubscribeEvent
    public static void onModifyBaking(ModelEvent.ModifyBakingResult event) {
        registerGlassCT(event, "lightweight_glass");
        registerGlassCT(event, "ultralight_glass");
        registerCasingCT(event, "starlight_casing");
        registerEncasedPipeCT(event, "starlight_encased_fluid_pipe");
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

    // 机壳 CT + CasingConnectivity: EncasedCTBehaviour.connectsTo 依赖 CasingConnectivity.get(state) 查找相邻
    // 同机壳 entry, 所以 CT 包装和 CasingConnectivity 注册必须用同一 CTSpriteShiftEntry. 等价 Create 的
    // BuilderTransformers.casing(connectedTextures(EncasedCTBehaviour) + casingConnectivity(makeCasing)).
    private static void registerCasingCT(ModelEvent.ModifyBakingResult event, String name) {
        ResourceLocation blockId = ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", name);
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == Blocks.AIR) return;
        CTSpriteShiftEntry shift = CTSpriteShifter.getCT(
                AllCTTypes.OMNIDIRECTIONAL,
                ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", "block/" + name),
                ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", "block/" + name + "_connected"));
        CreateClient.CASING_CONNECTIVITY.makeCasing(block, shift);
        ConnectedTextureBehaviour behaviour = new EncasedCTBehaviour(shift);
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation mrl = BlockModelShaper.stateToModelLocation(blockId, state);
            BakedModel original = event.getModels().get(mrl);
            if (original != null) {
                event.getModels().put(mrl, new CTModel(original, behaviour));
            }
        }
    }

    // 套壳管道 CT + CasingConnectivity: 用 starlight_casing 的 shift(机壳面连接), 只在非管道连接方向
    // 显示机壳连接(仿 Create encased_fluid_pipe 的 predicate: !getValue(FACING_TO_PROPERTY_MAP)).
    private static void registerEncasedPipeCT(ModelEvent.ModifyBakingResult event, String name) {
        ResourceLocation blockId = ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", name);
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == Blocks.AIR) return;
        CTSpriteShiftEntry shift = CTSpriteShifter.getCT(
                AllCTTypes.OMNIDIRECTIONAL,
                ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", "block/starlight_casing"),
                ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", "block/starlight_casing_connected"));
        CreateClient.CASING_CONNECTIVITY.make(block, shift,
                (s, f) -> !s.getValue(EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(f)));
        ConnectedTextureBehaviour behaviour = new EncasedCTBehaviour(shift);
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation mrl = BlockModelShaper.stateToModelLocation(blockId, state);
            BakedModel original = event.getModels().get(mrl);
            if (original != null) {
                // 套壳管道必须再包一层 PipeAttachmentModel: 法兰连接件(RIM_CONNECTOR/DRAIN/RIM)是
                // 由它在 gatherModelData 读 FluidTransportBehaviour.getRenderedRimAttachment 后, 在
                // addQuads 从 AllPartialModels.PIPE_ATTACHMENTS 拼装的 3D 部件, 不是贴图/blockstate 画的.
                // CT 管静态六面板机壳纹理, PipeAttachmentModel 管动态法兰几何, 互不干扰.
                // 包装顺序: PipeAttachmentModel(CTModel(original)) — super.getQuads 先走 CT 再拼 partials.
                BakedModel wrapped = new CTModel(original, behaviour);
                event.getModels().put(mrl, PipeAttachmentModel.withAO(wrapped));
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

    // 寻址牌 shift+滚轮切换选中地址:上滚=上一个(deltaY>0 -> -1),下滚=下一个,停末页不回绕。
    // cancel 事件防止 vanilla 潜行滚轮切物品栏。
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isShiftKeyDown()) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != BlockHitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        if (mc.level == null || !(mc.level.getBlockState(pos).getBlock() instanceof GlowSignBlock)) return;
        if (!(mc.level.getBlockEntity(pos) instanceof GlowSignBlockEntity be)) return;

        int size = be.getAddresses().size();
        if (size == 0) return;
        int current = be.getSelected();
        int delta = event.getScrollDeltaY() > 0 ? -1 : 1;
        int next = Mth.clamp(current + delta, 0, size - 1);
        if (next == current) return;
        mc.getConnection().send(new GlowSignScrollPayload(pos, next));
        event.setCanceled(true);
    }
}
