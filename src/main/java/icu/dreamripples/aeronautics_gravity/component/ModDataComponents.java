package icu.dreamripples.aeronautics_gravity.component;

import com.mojang.serialization.Codec;
import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 本 mod 自定义 DataComponent 注册入口. 物品/方块上的额外数据通过 DataComponent 持久
 * (写入 NBT) + 网络同步 (服务端→客户端 ItemStack spawn 数据).
 * <p>
 * 参考:Create {@code AllDataComponents} 使用 vanilla {@code DataComponentType.Builder}.
 */
public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AeronauticsGravityVisualization.MOD_ID);

    /**
     * 激活的末影珍珠:记录拥有者玩家 UUID. 玩家主手 ender_pearl + 副手 echo_shard 右键时由
     * {@code ActivatedEnderPearlHandler} 写入;读取发生在 {@code PackageEntityMixin} 中检测
     * 包裹静止 3 秒后破裂传送.
     * <p>
     * persistent ({@link UUIDUtil#CODEC}) 使其写入 ItemStack NBT 持久化;networkSynchronized
     * ({@link UUIDUtil#STREAM_CODEC}) 让客户端 ItemStack (tooltip/lore) 也能拿到 UUID.
     */
    public static final Supplier<DataComponentType<UUID>> ACTIVATED_PEARL_OWNER =
            DATA_COMPONENTS.register("activated_pearl_owner",
                    () -> DataComponentType.<UUID>builder()
                            .persistent(UUIDUtil.CODEC)
                            .networkSynchronized(UUIDUtil.STREAM_CODEC)
                            .build());
}