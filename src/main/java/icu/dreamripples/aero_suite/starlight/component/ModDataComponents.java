package icu.dreamripples.aero_suite.starlight.component;

import icu.dreamripples.aero_suite.starlight.StarlightLogistics;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 本 mod 自定义 DataComponent 注册入口. 物品/方块上的额外数据通过 DataComponent 持久
 * (写入 NBT) + 网络同步 (服务端→客户端 ItemStack spawn 数据).
 * <p>
 * 参考:Create {@code AllDataComponents} 使用 vanilla {@code DataComponentType.Builder}.
 */
public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, StarlightLogistics.MOD_ID);

    /**
     * 激活的末影珍珠:记录拥有者玩家 UUID + 玩家名. 玩家主手 ender_pearl + 副手 echo_shard
     * 右键时由 {@code ActivatedEnderPearlHandler} 写入;读取发生在 {@code PackageEntityMixin}
     * 中检测包裹静止 3 秒后破裂传送.
     * <p>
     * 使用 {@link PearlOwner} record 而非裸 UUID 的目的: 客户端 tooltip 可直接展示 name,
     * 不需要通过 UUID 反查玩家列表(客户端反查只在当前维度有效, 且离线玩家无记录).
     * <p>
     * persistent / networkSynchronized 使用 {@link PearlOwner#CODEC} / {@link PearlOwner#STREAM_CODEC}.
     */
    public static final Supplier<DataComponentType<PearlOwner>> ACTIVATED_PEARL_OWNER =
            DATA_COMPONENTS.register("activated_pearl_owner",
                    () -> DataComponentType.<PearlOwner>builder()
                            .persistent(PearlOwner.CODEC)
                            .networkSynchronized(PearlOwner.STREAM_CODEC)
                            .build());
}