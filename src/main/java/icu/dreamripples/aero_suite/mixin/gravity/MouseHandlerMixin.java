package icu.dreamripples.aero_suite.mixin.gravity;

import icu.dreamripples.aero_suite.gravity.client.ExtendoGrabClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 机械手载具抓取的姿态控制: 拖拽确认后按住姿态键(默认 Tab)时, 把本帧鼠标位移改写为载具目标姿态
 * 并跳过 vanilla 视角转动; 其余情况原样放行。
 * <p>@Redirect 拦截 {@code MouseHandler.turnPlayer} 里的 {@code player.turn(d0, d1 * invert)}
 * 调用 -- 捕获的是灵敏度/电影模式/纵向反转处理后的最终位移(与 Simulated 创造手杖 onMouseMove
 * 拿到的入参一致), 无需 MixinExtras @Local, 也天然规避其注解可用性问题(仓库惯例优先标准 @Redirect)。
 * 仅 client 端 mixin。
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Redirect(method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void aeroSuite$onTurnPlayer(LocalPlayer player, double yaw, double pitch) {
        if (ExtendoGrabClient.onMouseMove(yaw, pitch)) {
            return;   // 旋转模式: 鼠标位移已灌入载具姿态, 视角不动
        }
        player.turn(yaw, pitch);
    }
}
