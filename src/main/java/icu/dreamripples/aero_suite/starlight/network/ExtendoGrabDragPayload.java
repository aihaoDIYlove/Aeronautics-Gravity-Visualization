package icu.dreamripples.aero_suite.starlight.network;

import icu.dreamripples.aero_suite.gravity.GravityVisualization;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

/**
 * 机械手载具抓取 拖拽数据(C2S): 服务端确认后每个客户端 tick 发送(照搬 Simulated 创造手杖节奏)。
 * <ul>
 *   <li>playerRelativeGoal = 视线方向 × 拉伸距离(玩家相对偏移; 服务端加回插值眼位得到世界目标点,
 *       规避客户端/服务端眼位时差)</li>
 *   <li>localAnchor = 抓取锚点(sublevel 本地坐标, 即命中方块中心; 透传保持与创造手杖一致。
 *       服务端刻意忽略此字段 -- 锚点以建立会话时校验过的值为准, 透传仅为协议对齐)</li>
 *   <li>orientation = 目标姿态四元数(服务端把它作为约束坐标系, Tab 旋转时由客户端改写;
 *       服务端校验有限性并归一化)</li>
 * </ul>
 */
public record ExtendoGrabDragPayload(UUID subLevel, Vector3dc playerRelativeGoal, Vector3dc localAnchor,
                                     Quaterniondc orientation) implements CustomPacketPayload {
    public static final Type<ExtendoGrabDragPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GravityVisualization.MOD_ID, "extendo_grab_drag"));

    public static final StreamCodec<ByteBuf, ExtendoGrabDragPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                writeUuid(buf, value.subLevel());
                writeVec(buf, value.playerRelativeGoal());
                writeVec(buf, value.localAnchor());
                writeQuat(buf, value.orientation());
            },
            buf -> new ExtendoGrabDragPayload(readUuid(buf), readVec(buf), readVec(buf), readQuat(buf)));

    // netty ByteBuf 无 writeUUID(工具链内不可用), 手写两个 long, 顺序与 vanilla FriendlyByteBuf.writeUUID 一致
    private static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    private static void writeVec(ByteBuf buf, Vector3dc v) {
        buf.writeDouble(v.x());
        buf.writeDouble(v.y());
        buf.writeDouble(v.z());
    }

    private static Vector3d readVec(ByteBuf buf) {
        return new Vector3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writeQuat(ByteBuf buf, Quaterniondc q) {
        buf.writeDouble(q.x());
        buf.writeDouble(q.y());
        buf.writeDouble(q.z());
        buf.writeDouble(q.w());
    }

    private static Quaterniond readQuat(ByteBuf buf) {
        return new Quaterniond(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
