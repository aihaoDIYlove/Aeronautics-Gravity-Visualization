package icu.dreamripples.aeronautics_gravity.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -> 服务端空载荷包:玩家手持火花魔杖(护目镜代理)成功观察机器 goggle 数据时,
 * 由 GoggleOverlayRendererMixin 在客户端发送,服务端收到后触发 goggle_observe 成就触发器。
 * 无字段 -- 仅作信号,触发逻辑全在服务端 handler。
 */
public record ObserveMachinePayload() implements CustomPacketPayload {
    public static final Type<ObserveMachinePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", "observe_machine"));

    // 空载荷:unit 编解码器恒返回同一实例。playToServer 要求 StreamCodec<? super RegistryFriendlyByteBuf, T>,
    // StreamCodec<ByteBuf, T> 兼容(RegistryFriendlyByteBuf extends ByteBuf)。
    public static final StreamCodec<ByteBuf, ObserveMachinePayload> STREAM_CODEC =
            StreamCodec.unit(new ObserveMachinePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
