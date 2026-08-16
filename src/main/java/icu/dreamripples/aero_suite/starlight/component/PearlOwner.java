package icu.dreamripples.aero_suite.starlight.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * 激活的末影珍珠拥有者数据: UUID + 玩家名. 用 record 而非裸 UUID 的目的是让客户端
 * tooltip 直接读取存储的 name, 无需通过 UUID 反查玩家列表(客户端反查只在当前维度有效,
 * 且离线玩家无记录).
 *
 * @param uuid 拥有者玩家 UUID
 * @param name 拥有者玩家名(在绑定时刻保存, 客户端 tooltip 直接展示)
 */
public record PearlOwner(UUID uuid, String name) {

    public static final Codec<PearlOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(PearlOwner::uuid),
            Codec.STRING.fieldOf("name").forGetter(PearlOwner::name)
    ).apply(instance, PearlOwner::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PearlOwner> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, PearlOwner::uuid,
                    ByteBufCodecs.STRING_UTF8, PearlOwner::name,
                    PearlOwner::new
            );
}