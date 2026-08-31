package icu.dreamripples.aero_suite.starlight.network;

import icu.dreamripples.aero_suite.gravity.GravityVisualization;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 机械手载具抓取 开/停 请求(C2S)。
 * start=true 时服务端校验 subLevel + anchorPos:锚点方块必须是该载具上的绳索连接器,
 * 且玩家眼位在其触及距离(BLOCK_INTERACTION_RANGE, 伸缩机械手 +3/+5)内;否则静默忽略。
 * start=false 忽略其余字段, 释放当前玩家的拖拽会话。成功与否经 {@link ExtendoGrabSyncPayload} 回执。
 */
public record ExtendoGrabActionPayload(boolean start, UUID subLevel, BlockPos anchorPos) implements CustomPacketPayload {
    public static final Type<ExtendoGrabActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GravityVisualization.MOD_ID, "extendo_grab_action"));

    public static final StreamCodec<ByteBuf, ExtendoGrabActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ExtendoGrabActionPayload::start,
            UUIDUtil.STREAM_CODEC, ExtendoGrabActionPayload::subLevel,
            BlockPos.STREAM_CODEC, ExtendoGrabActionPayload::anchorPos,
            ExtendoGrabActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
