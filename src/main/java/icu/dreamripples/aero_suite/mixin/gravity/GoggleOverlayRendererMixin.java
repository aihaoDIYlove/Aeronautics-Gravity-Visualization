package icu.dreamripples.aero_suite.mixin.gravity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import icu.dreamripples.aero_suite.common.registry.ModItems;
import icu.dreamripples.aero_suite.starlight.network.ObserveMachinePayload;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * 监听 Create 的 GoggleOverlayRenderer.renderOverlay:用 @Redirect 拦截 addToGoggleTooltip 调用,
 * 调用原方法拿返回值;若返回 true(机器成功显示 goggle 数据)且玩家主/副手持有火花魔杖(护目镜代理),
 * 发送 ObserveMachinePayload 到服务端触发 goggle_observe 成就。
 *
 * 用 @Redirect(标准 Mixin)而非 @ModifyExpressionValue(MixinExtras),避免 1.21.1 注解可用性问题。
 * 去重:同一 BlockPos session 内只发一次,避免玩家持续 hover 同一机器时每帧发包。
 *
 * 注意:addToGoggleTooltip 只在 GogglesItem.isWearingGoggles 为 true 时被调用,而火花魔杖
 * 通过 addIsWearingPredicate 让该判定为 true,故走到这里已隐含"护目镜代理生效"。
 */
@Mixin(GoggleOverlayRenderer.class)
public abstract class GoggleOverlayRendererMixin {

    private static BlockPos aero_suite$lastObservePos = null;

    @Redirect(
            method = "renderOverlay",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/api/equipment/goggles/IHaveGoggleInformation;addToGoggleTooltip(Ljava/util/List;Z)Z")
    )
    private static boolean aero_suite$captureGoggleInfo(IHaveGoggleInformation gte,
                                                                 List<Component> tooltip, boolean isShifting) {
        boolean result = gte.addToGoggleTooltip(tooltip, isShifting);
        if (result) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ItemStack mainHand = mc.player.getMainHandItem();
                ItemStack offHand = mc.player.getOffhandItem();
                if (mainHand.is(ModItems.SPARK_WAND.get()) || offHand.is(ModItems.SPARK_WAND.get())) {
                    BlockPos currentPos = GoggleOverlayRenderer.lastHovered;
                    if (currentPos != null && !currentPos.equals(aero_suite$lastObservePos)) {
                        aero_suite$lastObservePos = currentPos;
                        ClientPacketListener conn = mc.getConnection();
                        if (conn != null) {
                            conn.send(new ObserveMachinePayload());
                        }
                    }
                }
            }
        }
        return result;
    }
}