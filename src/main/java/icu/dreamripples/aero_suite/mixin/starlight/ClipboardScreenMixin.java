package icu.dreamripples.aero_suite.mixin.starlight;

import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;
import icu.dreamripples.aero_suite.starlight.block.AddressingSignBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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
 * 改用 MixinExtras {@code @ModifyExpressionValue}:纯加性修改返回值,不替换原指令,与其他
 * mod 在同一条指令上的 @Redirect/注入共存(@Redirect 是排他的,双方都改写同一条指令时后应用者
 * 直接 InjectionError)。若 Create 未来改动 tick,安静失败(寻址牌 GUI 被关闭)而非崩溃。
 *
 * 不用 @Redirect/@ModifyExpressionValue on BlockEntry.has:Registrate 的 BlockEntry 不在
 * compile classpath(Create 的 jarjar)。getBlockState 的 owner 是 vanilla ClientLevel,在 classpath。
 */
@Mixin(ClipboardScreen.class)
public abstract class ClipboardScreenMixin {

    @ModifyExpressionValue(method = "tick",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState aero_suite$letAddressingSignPass(BlockState original) {
        if (original.getBlock() instanceof AddressingSignBlock)
            return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "clipboard"))
                .defaultBlockState();
        return original;
    }
}