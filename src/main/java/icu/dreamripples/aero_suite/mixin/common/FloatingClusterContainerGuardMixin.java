package icu.dreamripples.aero_suite.mixin.common;

import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.floating_block.FloatingClusterContainer;
import icu.dreamripples.aero_suite.common.AeroSuite;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sable 2.0.3 NPE 崩服的防护 Mixin(根因已实锤,2026-08-17):
 * <pre>
 * 退出世界时 static FloatingBlockMaterialDataHandler.allMaterials 被清空(单机下客户端
 * 断开触发资源 reload,clear 后未重填),Server thread 最后一个物理 tick 消费滞留的
 * addedBlocks 队列时 getFloatingMaterial 解析为 null -> addFloatingBlock:61 静默
 * new FloatingBlockCluster(null)(源码 assert 生产环境关闭)-> 同批后续 add 在
 * cluster.getMaterial().equals() 处 NPE 崩服。
 * </pre>
 * 防护:addFloatingBlock 消费时材质为 null 则取消本次 add(丢弃该块的浮力数据--
 * 材质已不可解析,本来也算不出升力,语义正确),使集群列表永不出现 null cluster。
 * 保留 ERROR 日志便于观察触发频率;待 Sable 上游修复后移除。
 * 历史诊断记录:queueAdd/queueRemove 守卫的日志已删(那是无浮力材质块 plot 加载的
 * 正常路径,噪音);allMaterials.size=0 实测来自退出世界序列。
 */
@Mixin(FloatingClusterContainer.class)
public class FloatingClusterContainerGuardMixin {

    @Inject(method = "addFloatingBlock", at = @At("HEAD"), cancellable = true)
    private void aero_suite$guardAdd(BlockState state, org.joml.Vector3d pos, CallbackInfo ci) {
        if (PhysicsBlockPropertyHelper.getFloatingMaterial(state) == null) {
            AeroSuite.LOGGER.error(
                    "[AeroSuite] 防护: addFloatingBlock 材质解析为 null(退出时材质表清空窗口?), 已丢弃该块浮力数据避免 null cluster 崩服: state={}, allMaterials.size={}, 线程={}",
                    state, FloatingBlockMaterialDataHandler.allMaterials.size(),
                    Thread.currentThread().getName(),
                    new Exception("[AeroSuite] 调用栈"));
            ci.cancel();
        }
    }
}
