package icu.dreamripples.aeronautics_gravity.event;

import dev.simulated_team.simulated.content.end_sea.EndSeaPhysics;
import dev.simulated_team.simulated.content.end_sea.EndSeaPhysicsData;
import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import icu.dreamripples.aeronautics_gravity.item.ModItems;
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
 * 瓶装星空液体取液: 玩家手持玻璃瓶在末地之海区域 (y < {@link EndSeaPhysics#startY()}) 右键时,
 * 消耗一个玻璃瓶换得一个 {@link icu.dreamripples.aeronautics_gravity.item.ModItems#STARLIGHT_BOTTLE}.
 * <p>
 * 末地之海是 Simulated 的 region 级物理力场 (见 {@code EndSeaPhysics}), 不是 fluid/block,
 * 虚空里无 FluidState, 所以 vanilla 玻璃瓶取液机制 (射线 + getFluidState) 不适用 -- 必须自判
 * 区域 + 伪造交互. 区域判定统一用 {@link EndSeaPhysicsData#of(Level)} + {@link EndSeaPhysics#startY()},
 * 兼容所有配了末地之海的维度 (the_end / the_aether 等), 不硬编码 {@code Level.END}.
 * <p>
 * 事件两端触发: 服务端执行消耗/给予 (Create {@code ManualApplicationRecipe} 同款 add+drop 失败回退模式);
 * 客户端仅 {@code setCancellationResult(SUCCESS)} 让手部动画播放. {@code setCanceled} 阻止 vanilla
 * {@code BottleItem.use} 的取水逻辑 (末地之海无水本不会触发, 保险起见仍取消).
 * <p>
 * 对着物理结构方块右键会走 {@code RightClickBlock}/useOn 而不触发本事件, 所以只有对着虚空右键才会取液,
 * 符合"在末地之海里舀一瓶"的预期.
 */
@EventBusSubscriber(modid = AeronauticsGravityVisualization.MOD_ID)
public class StarlightBottleHandler {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !held.is(Items.GLASS_BOTTLE))
            return;

        Level level = event.getLevel();
        EndSeaPhysics physics = EndSeaPhysicsData.of(level);
        if (physics == null)
            return;

        Player player = event.getEntity();
        if (player.getY() >= physics.startY())
            return;

        if (level.isClientSide()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        InteractionHand hand = event.getHand();
        ItemStack bottle = new ItemStack(ModItems.STARLIGHT_BOTTLE.get());
        held.shrink(1);
        if (held.isEmpty()) {
            player.setItemInHand(hand, bottle);
        } else if (!player.getInventory().add(bottle)) {
            player.drop(bottle, false);
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
