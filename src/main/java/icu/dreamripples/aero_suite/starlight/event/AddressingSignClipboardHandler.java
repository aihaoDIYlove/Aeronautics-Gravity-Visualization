package icu.dreamripples.aero_suite.starlight.event;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides;
import icu.dreamripples.aero_suite.starlight.StarlightLogistics;
import icu.dreamripples.aero_suite.starlight.block.AddressingSignBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 寻址牌剪贴板复制/粘贴地址列表。交互习惯对齐 Create 的 ClipboardValueSettingsHandler
 * (右键复制/左键粘贴),但那个 handler 要求目标为 SmartBlockEntity,寻址牌是纯
 * SignBlockEntity 进不去,故自写事件订阅器。
 *
 * 存储:复用剪贴板物品 CLIPBOARD_CONTENT.copiedValues(Create 存"复制的方块设置"的同一
 * 隐藏字段,不占可见页面,无需新数据组件)。只存 pages,其余字段重置为默认。
 *
 * 交互:
 * <ul>
 *   <li>手持剪贴板<b>普通右键</b>寻址牌 = 复制该牌地址列表进 copiedValues(不 shift,
 *       shift+右键仍走 AddressingSignBlock.useWithoutItem 打开编辑 GUI)。</li>
 *   <li>手持剪贴板<b>左键</b>寻址牌 = 粘贴 copiedValues 到该牌(无复制数据时不拦截,
 *       左键正常破坏方块)。粘贴走 setComponents -> 复用 ClipboardEditPacketMixin 同款
 *       重算路径(clamp selected + updateSignTextFromSelected)。</li>
 * </ul>
 */
@EventBusSubscriber(modid = StarlightLogistics.MOD_ID)
public class AddressingSignClipboardHandler {

    private static final String KEY = "AddressingSignAddresses";

    /**
     * AllBlocks.CLIPBOARD 是 Registrate BlockEntry,不在 compile classpath
     * (同 ClipboardScreenMixin 注释),故按物品 id 判断。
     */
    private static boolean isClipboard(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem())
            .equals(ResourceLocation.fromNamespaceAndPath("create", "clipboard"));
    }

    /** 手持剪贴板的普通右键:复制寻址牌地址列表 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isClipboard(event.getItemStack())) return;
        Player player = event.getEntity();
        if (player.isShiftKeyDown()) return; // shift+右键 = 编辑 GUI(AddressingSignBlock 处理)
        Level level = event.getLevel();
        if (!(level.getBlockEntity(event.getPos()) instanceof AddressingSignBlockEntity sign)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide) return;

        ItemStack clipboard = event.getItemStack();
        ClipboardContent content = clipboard.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);

        var addresses = sign.getAddresses(); // List<String>,非空才存
        if (addresses.isEmpty()) {
            feedback(player, "message.starlight_logistics.addressing_sign.copy_empty", ChatFormatting.RED);
            return;
        }

        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, String.join("\n", addresses));
        content = content.setCopiedValues(tag).setType(ClipboardOverrides.ClipboardType.WRITTEN);
        clipboard.set(AllDataComponents.CLIPBOARD_CONTENT, content);

        level.playSound(null, event.getPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.25f, 1.125f);
        feedback(player, "message.starlight_logistics.addressing_sign.copied", ChatFormatting.GREEN);
    }

    /** 手持剪贴板的左键:粘贴地址列表到寻址牌 */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!isClipboard(event.getItemStack())) return;
        Player player = event.getEntity();
        Level level = event.getLevel();
        if (!(level.getBlockEntity(event.getPos()) instanceof AddressingSignBlockEntity sign)) return;

        ClipboardContent content = event.getItemStack().get(AllDataComponents.CLIPBOARD_CONTENT);
        CompoundTag copied = content == null ? null : content.copiedValues().orElse(null);
        if (copied == null || !copied.contains(KEY)) return; // 无复制数据:不拦截,正常破坏

        event.setCanceled(true);
        if (level.isClientSide) return;

        String joined = copied.getString(KEY);
        if (joined.isBlank()) {
            feedback(player, "message.starlight_logistics.addressing_sign.copy_empty", ChatFormatting.RED);
            return;
        }
        List<ClipboardEntry> entries = new ArrayList<>();
        for (String line : joined.split("\n")) {
            if (!line.isBlank())
                entries.add(new ClipboardEntry(false, Component.literal(line.trim())));
        }
        if (entries.isEmpty()) {
            feedback(player, "message.starlight_logistics.addressing_sign.copy_empty", ChatFormatting.RED);
            return;
        }

        // 写入 components(setComponents 触发 clamp + updateSignTextFromSelected),与
        // ClipboardEditPacketMixin 同款路径;selected 保持不变,若越界会被 clamp
        PatchedDataComponentMap map = new PatchedDataComponentMap(sign.components());
        map.set(AllDataComponents.CLIPBOARD_CONTENT,
                content.setPages(List.of(entries)).setType(ClipboardOverrides.ClipboardType.WRITTEN));
        sign.setComponents(map);
        sign.onClipboardEdited(player);

        level.playSound(null, event.getPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.25f, 2f);
        feedback(player, "message.starlight_logistics.addressing_sign.pasted", ChatFormatting.GREEN);
    }

    private static void feedback(Player player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(key).withStyle(color), true);
    }
}
