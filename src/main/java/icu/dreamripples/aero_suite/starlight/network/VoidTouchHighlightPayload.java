package icu.dreamripples.aero_suite.starlight.network;

import icu.dreamripples.aero_suite.starlight.StarlightLogistics;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 虚空之触连锁高亮(S2C)。服务端 BFS 计算连锁集合后下发客户端渲染(透视线框);
 * positions 为空 = 清除当前高亮(破坏完成/过期)。
 */
public record VoidTouchHighlightPayload(List<BlockPos> positions) implements CustomPacketPayload {
    public static final Type<VoidTouchHighlightPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarlightLogistics.MOD_ID, "void_touch_highlight"));

    public static final StreamCodec<ByteBuf, VoidTouchHighlightPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(java.util.ArrayList::new, BlockPos.STREAM_CODEC), VoidTouchHighlightPayload::positions,
            VoidTouchHighlightPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
