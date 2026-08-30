package icu.dreamripples.aero_suite.starlight.network;

import icu.dreamripples.aero_suite.gravity.GravityVisualization;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * 机械手载具抓取 会话回执(S2C): dragging=true 表示服务端已建立拖拽会话(附权威拉伸距离,
 * 客户端据此计算目标点与服务端一致); false 表示会话已结束(客户端主动停止/服务端任意释放原因)。
 * 客户端仅凭回执进入/退出拖拽状态, 杜绝"客户端自以为在拖"的漂移态。
 * clientListener 由客户端类(ExtendoGrabClient)静态初始化时注册; 服务端恒 null,
 * 注册处的处理器只读该字段, 不引用任何客户端类(专用服安全)。
 */
public record ExtendoGrabSyncPayload(boolean dragging, double distance) implements CustomPacketPayload {
    public static final Type<ExtendoGrabSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GravityVisualization.MOD_ID, "extendo_grab_sync"));

    public static final StreamCodec<ByteBuf, ExtendoGrabSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ExtendoGrabSyncPayload::dragging,
            ByteBufCodecs.DOUBLE, ExtendoGrabSyncPayload::distance,
            ExtendoGrabSyncPayload::new);

    /** 客户端处理回调; 由 ExtendoGrabClient 类初始化时写入, 服务端恒 null。 */
    public static volatile Consumer<ExtendoGrabSyncPayload> clientListener;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
