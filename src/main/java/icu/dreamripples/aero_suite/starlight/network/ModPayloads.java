package icu.dreamripples.aero_suite.starlight.network;

import icu.dreamripples.aero_suite.gravity.extendo.ExtendoGrabServer;
import icu.dreamripples.aero_suite.starlight.block.AddressingSignBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 注册本 mod 的网络包(寻址牌滚轮 C2S、机械手抓取 C2S/S2C)。
 */
public class ModPayloads {
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModPayloads::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                AddressingSignScrollPayload.TYPE,
                AddressingSignScrollPayload.STREAM_CODEC,
                ModPayloads::handleScroll
        );
        // 机械手载具抓取: action(开/停请求) + drag(拖拽中每 tick 数据) 走 C2S;
        // sync(会话回执) 走 S2C, handler 只读 clientListener 字段, 不引用任何客户端类(专用服安全)
        event.registrar("1").playToServer(
                ExtendoGrabActionPayload.TYPE,
                ExtendoGrabActionPayload.STREAM_CODEC,
                ModPayloads::handleExtendoGrabAction
        );
        event.registrar("1").playToServer(
                ExtendoGrabDragPayload.TYPE,
                ExtendoGrabDragPayload.STREAM_CODEC,
                ModPayloads::handleExtendoGrabDrag
        );
        event.registrar("1").playToClient(
                ExtendoGrabSyncPayload.TYPE,
                ExtendoGrabSyncPayload.STREAM_CODEC,
                ModPayloads::handleExtendoGrabSync
        );
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

    // 机械手载具抓取: 开/停请求(服务端权威校验绳索连接器/触及距离)
    private static void handleExtendoGrabAction(ExtendoGrabActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer sp) {
            ExtendoGrabServer.handleAction(payload, sp);
        }
    }

    // 机械手载具抓取: 拖拽中每 tick 的目标点/姿态上传
    private static void handleExtendoGrabDrag(ExtendoGrabDragPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer sp) {
            ExtendoGrabServer.handleDrag(payload, sp);
        }
    }

    // 机械手载具抓取: 会话回执 -> 客户端状态(clientListener 由客户端类注册, 服务端恒 null)
    private static void handleExtendoGrabSync(ExtendoGrabSyncPayload payload, IPayloadContext context) {
        var listener = ExtendoGrabSyncPayload.clientListener;
        if (listener != null) {
            context.enqueueWork(() -> listener.accept(payload));
        }
    }
}
