package icu.dreamripples.aeronautics_gravity.network;

import icu.dreamripples.aeronautics_gravity.advancement.ModTriggers;
import icu.dreamripples.aeronautics_gravity.block.AddressingSignBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 注册本 mod 的网络包。ObserveMachinePayload 走 playToServer:
 * 客户端(GoggleOverlayRendererMixin)发送 -> 服务端 handler 触发 goggle_observe 成就。
 */
public class ModPayloads {
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModPayloads::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                ObserveMachinePayload.TYPE,
                ObserveMachinePayload.STREAM_CODEC,
                ModPayloads::handle
        );
        event.registrar("1").playToServer(
                AddressingSignScrollPayload.TYPE,
                AddressingSignScrollPayload.STREAM_CODEC,
                ModPayloads::handleScroll
        );
    }

    private static void handle(ObserveMachinePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer sp) {
            ModTriggers.GOGGLE_OBSERVE.get().trigger(sp);
        }
    }

    // 寻址牌滚轮切换选中地址(C2S):服务端验证交互距离后 setSelected(内含 clamp + SignText 同步)
    private static void handleScroll(AddressingSignScrollPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer sp) {
            var level = sp.serverLevel();
            if (!level.isLoaded(payload.pos())) return;
            if (!sp.canInteractWithBlock(payload.pos(), 4.0)) return;
            if (level.getBlockEntity(payload.pos()) instanceof AddressingSignBlockEntity be) {
                be.setSelected(be.clampSelected(payload.newSelected()));
            }
        }
    }
}
