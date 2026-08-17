package icu.dreamripples.aero_suite.starlight.event;

import icu.dreamripples.aero_suite.common.registry.ModItems;
import icu.dreamripples.aero_suite.starlight.StarlightLogistics;
import icu.dreamripples.aero_suite.starlight.component.PearlOwner;
import icu.dreamripples.aero_suite.starlight.event.StarlightBottleHandler;
import icu.dreamripples.aero_suite.starlight.item.ActivatedEnderPearlItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 激活末影珍珠合成: 玩家主手 {@code ender_pearl} + 副手 {@code echo_shard} 右键时,
 * 消耗两物, 产出一颗 {@link ActivatedEnderPearlItem}(携带玩家 UUID)放回主手或进背包/丢地.
 * <p>
 * 必须 {@code setCanceled(true) + setCancellationResult(SUCCESS)} 阻止 vanilla
 * {@code EnderpearlItem.use} 在同帧把末影珍珠实际掷出去(与 {@code StarlightBottleHandler}
 * 同款的取消模式). 客户端只做 cancel, 服务端做实际物品交换.
 * <p>
 * 与 {@code StarlightBottleHandler} 同形: 主手目标物 + 副手材料 → 右键替换 + 消耗.
 */
@EventBusSubscriber(modid = StarlightLogistics.MOD_ID)
public class ActivatedEnderPearlHandler {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        // 配置停用时禁止激活(合成途径的事件等价物; 获取到的激活珍珠本身会被 DisabledItemCollector 删除)
        if (!icu.dreamripples.aero_suite.common.config.FeatureGates.isEnabled("activated_pearl")) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack main = event.getItemStack();
        if (!main.is(Items.ENDER_PEARL)) return;
        Player player = event.getEntity();
        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (!offHand.is(Items.ECHO_SHARD)) return;

        Level level = event.getLevel();
        if (level.isClientSide()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        // 服务端: 产出激活珍珠, 消耗两物, 优先放回主手(若该格已空) 否则入背包或丢出
        ItemStack activated = new ItemStack(ModItems.ACTIVATED_ENDER_PEARL.get());
        ActivatedEnderPearlItem.setOwner(activated,
                    new PearlOwner(player.getUUID(), player.getName().getString()));
        main.shrink(1);     // 消耗主手末影珍珠
        offHand.shrink(1);  // 消耗副手回响碎片
        if (main.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, activated);
        } else if (!player.getInventory().add(activated)) {
            player.drop(activated, false);  // 背包满 -> 丢出
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}