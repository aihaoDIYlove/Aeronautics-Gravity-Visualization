package icu.dreamripples.aeronautics_gravity.item;

import icu.dreamripples.aeronautics_gravity.component.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * 被激活的末影珍珠: 携带拥有者玩家 UUID (见
 * {@link ModDataComponents#ACTIVATED_PEARL_OWNER}). 装入 Create Package 后, 当包裹
 * 作为实体静止 3 秒破裂时, 由 {@code PackageEntityMixin} 读取 UUID, 生成一颗向下飞行
 * 的 {@code ThrownEnderpearl}(owner=该 UUID), 落地由 vanilla {@code onHit} 跨维度
 * 传送拥有者到落点 + 5 血 + 音效/粒子. 玩家离线时 vanilla {@code onHit} 内的
 * {@code connection.isAcceptingMessages()} 判否 -> 跳过传送 -> 珍珠消失(等同末影珍珠
 * 被销毁).
 * <p>
 * <b>获得方式</b>:(1) 主手 ender_pearl + 副手 echo_shard 右键直接合成(已带 UUID, 见
 * {@code ActivatedEnderPearlHandler});(2) 创造页拿到的本物品<b>不带 UUID</b>(无流光),
 * 玩家可 {@link #use} 右键将当前玩家 UUID 写入手上珍珠(写后即为有效激活珍珠).
 * <p>
 * <b>视觉区分</b>: 重写 {@link #isFoil(ItemStack)} -- 仅当 UUID 已写入时显示原版附魔流光,
 * 一眼区分"有效(已绑定玩家)"与"空模板(创造页直接拿出的)".
 */
public class ActivatedEnderPearlItem extends Item {
    public ActivatedEnderPearlItem(Properties properties) {
        super(properties);
    }

    /** 读取激活珍珠记录的拥有者 UUID; 该组件未设置时返回 null. */
    public static UUID getOwner(ItemStack stack) {
        return stack.get(ModDataComponents.ACTIVATED_PEARL_OWNER.get());
    }

    public static void setOwner(ItemStack stack, UUID uuid) {
        stack.set(ModDataComponents.ACTIVATED_PEARL_OWNER.get(), uuid);
    }

    /**
     * 仅当已写入拥有者 UUID 时显示附魔流光(原版紫色光晕),
     * 用来一眼区分"有效激活珍珠"(已绑定玩家)与"空模板"(创造页直接拿出, 未写 UUID).
     * 不依赖 {@code DataComponents.ENCHANTMENTS}(1.21.1 附魔 data-driven, 伪造空附魔不合适);
     * 直接重写 isFoil 最干净.
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        return getOwner(stack) != null;
    }

    /**
     * 右键写入当前玩家 UUID. 创造页直接拿到的激活珍珠无 UUID, 无流光, 玩家右键即可绑定自己.
     * <ul>
     *   <li>已有 UUID -> 不动(返回 PASS, 让其它事件链有机会).</li>
     *   <li>stackSize == 1 -> 直接给手上这颗写 UUID.</li>
     *   <li>stackSize &gt; 1 -> 拷一份 count=1 的 stack 写 UUID 放回背包/丢出, 原堆 shrink 1.
     *       必须这样, 否则给整堆写会让 DataComponent 一份 UUID 共享到全部 16 颗 -- 放进包裹
     *       仍只算一个 owner, 多传没意义.</li>
     * </ul>
     * 不消耗玩家物品本身激活珍珠这一物品就是"绑定后的产物".
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (getOwner(stack) != null) {
            // 已绑定: 不做事, 让玩家原意(use 上没有其它行为)走 PASS
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide()) {
            // 客户端先用结果触发一次动画/手感
            return InteractionResultHolder.success(stack);
        }

        // 服务端写 UUID
        UUID owner = player.getUUID();
        if (stack.getCount() == 1) {
            setOwner(stack, owner);
        } else {
            // 拷一份写 UUID, 原堆减 1, 放回背包或丢出
            stack.shrink(1);
            ItemStack bound = stack.copyWithCount(1);
            setOwner(bound, owner);
            if (!player.getInventory().add(bound)) {
                player.drop(bound, false);
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_PEARL_THROW, SoundSource.NEUTRAL, 0.4F,
                1.6F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

        // 不消耗手上激活珍珠本物品 -- 绑定不是合成
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        UUID owner = getOwner(stack);
        if (owner != null) {
            tooltip.add(Component.translatable("tooltip.aeronautics_gravity.activated_ender_pearl.bound")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltip.add(Component.translatable("tooltip.aeronautics_gravity.activated_ender_pearl.unbound")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.aeronautics_gravity.activated_ender_pearl")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}