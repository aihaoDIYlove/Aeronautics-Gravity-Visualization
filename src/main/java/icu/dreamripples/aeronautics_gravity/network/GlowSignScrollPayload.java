package icu.dreamripples.aeronautics_gravity.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 发光告示牌滚轮切换选中地址(C2S)。客户端 shift+滚轮时发送,服务端 handler 验证后调用
 * {@code GlowSignBlockEntity.setSelected}。携带 newSelected 而非 delta:服务端 clamp 防越界,
 * 且客户端已计算目标值(停末页不回绕)。
 */
public record GlowSignScrollPayload(BlockPos pos, int newSelected) implements CustomPacketPayload {
    public static final Type<GlowSignScrollPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aeronautics_gravity", "glow_sign_scroll"));

    public static final StreamCodec<ByteBuf, GlowSignScrollPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, GlowSignScrollPayload::pos,
            ByteBufCodecs.VAR_INT, GlowSignScrollPayload::newSelected,
            GlowSignScrollPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
