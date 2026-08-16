package icu.dreamripples.aero_suite.starlight.network;
import icu.dreamripples.aero_suite.starlight.StarlightLogistics;

import io.netty.buffer.ByteBuf;
import icu.dreamripples.aero_suite.starlight.block.AddressingSignBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 寻址牌滚轮切换选中地址(C2S)。客户端 shift+滚轮时发送,服务端 handler 验证后调用
 * {@code AddressingSignBlockEntity.setSelected}。携带 newSelected 而非 delta:服务端 clamp 防越界,
 * 且客户端已计算目标值(停末页不回绕)。
 */
public record AddressingSignScrollPayload(BlockPos pos, int newSelected) implements CustomPacketPayload {
    public static final Type<AddressingSignScrollPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarlightLogistics.MOD_ID, "addressing_sign_scroll"));

    public static final StreamCodec<ByteBuf, AddressingSignScrollPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AddressingSignScrollPayload::pos,
            ByteBufCodecs.VAR_INT, AddressingSignScrollPayload::newSelected,
            AddressingSignScrollPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
