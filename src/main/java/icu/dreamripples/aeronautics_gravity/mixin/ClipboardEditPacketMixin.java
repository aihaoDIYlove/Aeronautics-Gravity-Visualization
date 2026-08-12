package icu.dreamripples.aeronautics_gravity.mixin;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEditPacket;
import icu.dreamripples.aeronautics_gravity.block.GlowSignBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link ClipboardEditPacket#handle} 原逻辑仅处理 {@code instanceof ClipboardBlockEntity} 的目标,
 * 否则 return(数据写不进发光告示牌)。本 Mixin 在 HEAD 注入:若目标是 {@link GlowSignBlockEntity},
 * 用同款 {@code PatchedDataComponentMap} + {@code setComponents} 写入 ClipboardContent,然后 cancel
 * 跳过原逻辑。非 GlowSign 目标直接 return,走原 ClipboardBlockEntity 分支不受影响。
 *
 * 选 @Inject HEAD + cancellable 而非 @Redirect:需访问 record 字段 {@code targetedBlock()} /
 * {@code clipboardContent()}(原方法局部变量 processedContent 在 handle 内部),@Redirect 拦截
 * getBlockEntity 拿不到这些。@Inject HEAD 能访问 this + cancel 后不影响原分支。
 */
@Mixin(ClipboardEditPacket.class)
public abstract class ClipboardEditPacketMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void aeronautics_gravity$handleGlowSign(ServerPlayer sender, CallbackInfo ci) {
        ClipboardEditPacket self = (ClipboardEditPacket) (Object) this;
        BlockPos targetedBlock = self.targetedBlock();
        if (targetedBlock == null) return;  // 物品编辑分支,走原逻辑

        Level world = sender.level();
        if (!world.isLoaded(targetedBlock)) return;
        if (!sender.canInteractWithBlock(targetedBlock, 20)) return;

        BlockEntity be = world.getBlockEntity(targetedBlock);
        if (!(be instanceof GlowSignBlockEntity glowSign)) return;  // 非 GlowSign,走原逻辑

        // 复用 Create 的 clipboardProcessor(过滤 ClickEvent)
        ClipboardContent processed = ClipboardEditPacket.clipboardProcessor(self.clipboardContent());
        PatchedDataComponentMap map = new PatchedDataComponentMap(glowSign.components());
        if (processed == null) {
            map.remove(AllDataComponents.CLIPBOARD_CONTENT);
        } else {
            map.set(AllDataComponents.CLIPBOARD_CONTENT, processed);
        }
        // setComponents 覆写内触发:recomputeAddresses + clamp selected + updateSignText(同步 SignText)
        glowSign.setComponents(map);
        glowSign.onClipboardEdited(sender);
        ci.cancel();
    }
}
