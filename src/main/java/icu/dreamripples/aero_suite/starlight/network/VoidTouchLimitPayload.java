package icu.dreamripples.aero_suite.starlight.network;

import icu.dreamripples.aero_suite.starlight.StarlightLogistics;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 虚空之触挡位调整(C2S)。客户端 Shift+滚轮 时发送, delta = +1(上滑增挡)/-1(下滑减挡)。
 * 服务端按挡位表 {1,2,4,8,16,32,48,64} 邻近移动并 clamp, 存入玩家 persistentData。
 */
public record VoidTouchLimitPayload(int delta) implements CustomPacketPayload {
    public static final Type<VoidTouchLimitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarlightLogistics.MOD_ID, "void_touch_limit"));

    public static final StreamCodec<ByteBuf, VoidTouchLimitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VoidTouchLimitPayload::delta,
            VoidTouchLimitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
