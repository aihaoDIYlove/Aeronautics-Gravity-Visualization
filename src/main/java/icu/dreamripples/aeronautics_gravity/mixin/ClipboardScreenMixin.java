package icu.dreamripples.aeronautics_gravity.mixin;

import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;
import icu.dreamripples.aeronautics_gravity.block.GlowSignBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 放宽 {@link ClipboardScreen#tick()} 的 CLIPBOARD 方块检查:原逻辑仅允许 Create 剪切板方块,
 * 否则 {@code minecraft.setScreen(null)} 强制关闭。本 Mixin 让寻址牌也能保持 ClipboardScreen 开启。
 *
 * 拦截 tick 里的 {@code Minecraft.setScreen} 调用(共两处:距离检查 + CLIPBOARD 方块检查)。
 * handler 判断:玩家仍在交互范围内(canInteractWithBlock=true,即距离检查已通过)+ targetedBlock
 * 是 GlowSign -> 跳过 setScreen(对应 CLIPBOARD 检查处)。距离检查处(玩家超出范围)正常关闭。
 *
 * 用 setScreen 而非 BlockEntry.has 拦截:Registrate 的 BlockEntry 不在 compile classpath
 * (Create 的 jarjar),@Redirect on BlockEntry.has 要求 owner 参数为 BlockEntry 类型(无法编译)。
 * setScreen 的 owner 是 vanilla Minecraft,在 classpath。
 */
@Mixin(ClipboardScreen.class)
public abstract class ClipboardScreenMixin {

    @Redirect(method = "tick",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void aeronautics_gravity$skipCloseForGlowSign(Minecraft mc, Screen screen) {
        ClipboardScreen self = (ClipboardScreen) (Object) this;
        BlockPos pos = self.targetedBlock;
        // 仅 CLIPBOARD 检查处跳过:玩家在范围内(距离检查已过)+ 方块是 GlowSign
        if (pos != null && mc.player != null && mc.level != null
                && mc.player.canInteractWithBlock(pos, 10)) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.getBlock() instanceof GlowSignBlock) return;  // 跳过 setScreen
        }
        mc.setScreen(screen);
    }
}
