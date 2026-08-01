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
 * 瓶装星空液体取液: 玩家手持玻璃瓶在末地之海区域 (y &lt; startY) 且视角朝下、对着虚空(未命中方块)
 * 右键时, 消耗玻璃瓶换得瓶装星空液体.
 * <p>
 * 末地之海是 region 级物理力场, 虚空无 FluidState, vanilla 玻璃瓶取液不适用, 必须自判区域+伪造交互.
 * 两个判定:
 * <ul>
 *   <li>{@link #onRightClickBlock}: 命中方块(载具地板/平台等)时取消 -- 玩家在末地海里能遮挡射线的只有
 *       载具本身, 对方块右键说明不是对着虚空, 不该装瓶. {@code cancellationResult=SUCCESS} 同时阻止
 *       方块 use 和后续 {@code RightClickItem}(物品 use), 从而阻止装瓶.</li>
 *   <li>{@link #onRightClickItem}: 未命中方块(对着虚空)时, 若视角朝下({@code pitch>0})才装瓶 --
 *       抬头朝天空不算对着虚空(下方).</li>
 * </ul>
 * <p>
 * 注: 本方案假设"对载具方块右键触发 RightClickBlock"(Sable 让客户端射线检测 SubLevel 载具)。
 * 若实测载具方块不触发 RightClickBlock, 需改用 SableRaycastHelper 在 RightClickItem 内做射线检测。
 */
@EventBusSubscriber(modid = AeronauticsGravityVisualization.MOD_ID)
public class StarlightBottleHandler {

    private static boolean isGlassBottle(ItemStack held) {
        return !held.isEmpty() && held.is(Items.GLASS_BOTTLE);
    }

    private static boolean inEndSeaRegion(Level level, Player player) {
        EndSeaPhysics physics = EndSeaPhysicsData.of(level);
        return physics != null && player.getY() < physics.startY();
    }

    // 命中方块(载具地板/平台等) -> 不装瓶. SUCCESS 阻止方块 use + 后续物品 use(RightClickItem).
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isGlassBottle(event.getItemStack())) return;
        if (!inEndSeaRegion(event.getLevel(), event.getEntity())) return;
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack held = event.getItemStack();
        if (!isGlassBottle(held)) return;
        Level level = event.getLevel();
        EndSeaPhysics physics = EndSeaPhysicsData.of(level);
        if (physics == null) return;
        Player player = event.getEntity();
        if (player.getY() >= physics.startY()) return;
        // 视线与 Y 轴(向下)夹角 < 45° 才装: 夹角 = 90 - pitch, 故 pitch > 45
        // (垂直下 pitch=90=夹角0, 水平 pitch=0=夹角90, pitch=45=夹角45 不装)
        if (player.getXRot() <= 45) return;
        if (level.isClientSide()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        // 服务端: 消耗玻璃瓶 + 给予瓶装星空液体 (Create ManualApplicationRecipe 同款 add+drop 失败回退)
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
