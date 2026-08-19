package icu.dreamripples.aero_suite.mixin.starlight;

import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;
import icu.dreamripples.aero_suite.starlight.block.AddressingSignBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 放宽 {@link ClipboardScreen#tick()} 的 CLIPBOARD 方块检查:原逻辑仅允许 Create 剪切板方块,
 * 否则 {@code minecraft.setScreen(null)} 强制关闭。
 *
 * 拦截 tick 里 CLIPBOARD 检查处的 {@code ClientLevel.getBlockState}:若目标是 AddressingSign,
 * 返回真正的剪切板默认 state,让 {@code AllBlocks.CLIPBOARD.has()} 自然通过,tick 继续走完。
 *
 * 历史踩坑:曾用 @Redirect 拦 {@code setScreen(null)} 跳过关闭,但原代码 setScreen 后紧跟
 * {@code return;},Redirect 只跳过调用不跳过 return -> tick 在悬停计算(hoveredEntry/
 * hoveredCheck,位于 tick 尾部)之前提前返回 -> mouseClicked 无法选中旧行。必须让 tick
 * 完整执行,故改为伪装方块 state 而非拦截 setScreen。
 *
 * 不用 @Redirect on BlockEntry.has:Registrate 的 BlockEntry 不在 compile classpath
 * (Create 的 jarjar)。getBlockState 的 owner 是 vanilla ClientLevel,在 classpath。
 */
@Mixin(ClipboardScreen.class)
public abstract class ClipboardScreenMixin {

    @Redirect(method = "tick",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState aero_suite$letAddressingSignPass(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AddressingSignBlock)
            return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "clipboard"))
                .defaultBlockState();
        return state;
    }
}