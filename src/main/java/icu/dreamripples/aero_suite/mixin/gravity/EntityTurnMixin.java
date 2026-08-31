package icu.dreamripples.aero_suite.mixin.gravity;

import icu.dreamripples.aero_suite.gravity.client.ExtendoGrabClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 机械手载具抓取的姿态控制: 拖拽确认后按住姿态键(默认 Tab)时, 把本帧鼠标位移改写为载具目标姿态
 * 并跳过 vanilla 视角转动; 其余情况原样放行。
 *
 * <p>注入点刻意选在被调方法本体 {@code Entity.turn} 而非 {@code MouseHandler.turnPlayer} 内部
 * 的 {@code player.turn} 调用指令: Simulated 的 hold_interaction MouseHandlerMixin 用 cancellable
 * {@code @Inject} 挂在同一条调用指令上, 若本 mod 在同一点用 {@code @Redirect} 拦截方法调用
 * (仓库一般惯例), 后应用的 mixin 会因原指令已被替换而找不到注入目标(InjectionError),
 * 能否共存完全取决于 mod 加载顺序。挂到被调方法 HEAD 是加性注入, 与任何顺序/任何同点注入都兼容。
 * {@code turn(DD)} 的两个入参就是 turnPlayer 里灵敏度/电影模式/纵向反转处理后的最终位移
 * (与 Simulated 创造手杖 onMouseMove 入参一致, 数值无需换算); vanilla 中该方法唯一调用方
 * 就是 MouseHandler.turnPlayer, 继承链无覆写, 语义与拦截调用指令完全等价。
 * 仅 client 端 mixin(target 是公共类, 但回调只触客户端逻辑且本类仅在 client 数组应用)。
 */
@Mixin(Entity.class)
public class EntityTurnMixin {

    @Inject(method = "turn(DD)V", cancellable = true, at = @At("HEAD"))
    private void aeroSuite$onTurn(double yaw, double pitch, CallbackInfo ci) {
        if (ExtendoGrabClient.onMouseMove(yaw, pitch)) {
            ci.cancel();   // 旋转模式: 鼠标位移已灌入载具姿态, 视角不动
        }
    }
}
