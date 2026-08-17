package icu.dreamripples.aero_suite.starlight.item;

import icu.dreamripples.aero_suite.starlight.component.ModDataComponents;
import icu.dreamripples.aero_suite.mixin.starlight.PackageEntityMixin;
import icu.dreamripples.aero_suite.starlight.component.PearlOwner;
import icu.dreamripples.aero_suite.starlight.event.ActivatedEnderPearlHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * 被激活的末影珍珠: 携带拥有者玩家 {@link PearlOwner}(UUID + 玩家名), 装入 Create
 * Package 后, 当包裹作为实体静止 3 秒破裂时, 由 {@code PackageEntityMixin} 读取 UUID,
 * 生成一颗向下飞行的 {@code ThrownEnderpearl}(owner=该 UUID), 落地由 vanilla
 * {@code onHit} 跨维度传送拥有者到落点 + 5 血 + 音效/粒子.
 * <p>
 * <b>获得方式</b>:
 * <ul>
 *   <li>主手 ender_pearl + 副手 echo_shard 右键合成(见 {@code ActivatedEnderPearlHandler})</li>
 *   <li>创造页拿到的无 PearlOwner 模板, 右键写入当前玩家(见 {@link #use})</li>
 * </ul>
 * <b>视觉区分</b>: 重写 {@link #isFoil(ItemStack)} -- 仅当已写入时显示原版附魔流光.
 */
public class ActivatedEnderPearlItem extends Item {
    public ActivatedEnderPearlItem(Properties properties) {
        super(properties);
    }

    /** 读取激活珍珠记录的拥有者数据; 该组件未设置时返回 null. */
    public static PearlOwner getOwner(ItemStack stack) {
        return stack.get(ModDataComponents.ACTIVATED_PEARL_OWNER.get());
    }

    /** 纯便利: 直接取 UUID. 用于 PackageEntityMixin 中仅需 UUID 传 setOwner. */
    public static UUID getOwnerUuid(ItemStack stack) {
        PearlOwner owner = getOwner(stack);
        return owner != null ? owner.uuid() : null;
    }

    public static void setOwner(ItemStack stack, PearlOwner owner) {
        stack.set(ModDataComponents.ACTIVATED_PEARL_OWNER.get(), owner);
    }

    /**
     * 仅当已写入拥有者数据时显示附魔流光(原版紫色光晕),
     * 用来一眼区分"有效激活珍珠"(已绑定玩家)与"空模板".
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        return getOwner(stack) != null;
    }

    /**
     * 右键写入当前玩家. 创造页直接拿到的激活珍珠无 Owner 数据, 无流光, 玩家右键即可绑定自己.
     * <ul>
     *   <li>已有 Owner -> 不动(返回 PASS).</li>
     *   <li>stackSize == 1 -> 直接给手上这颗写 Owner(含 uuid + 当前玩家名).</li>
     *   <li>stackSize &gt; 1 -> 拷一份 count=1 写 Owner, 原堆 shrink 1, 放回背包/丢出.</li>
     * </ul>
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (getOwner(stack) != null) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        PearlOwner owner = new PearlOwner(player.getUUID(), player.getName().getString());
        if (stack.getCount() == 1) {
            setOwner(stack, owner);
        } else {
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

        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        PearlOwner owner = getOwner(stack);
        if (owner != null) {
            tooltip.add(Component.translatable("tooltip.starlight_logistics.activated_ender_pearl.bound")
                    .append(Component.literal(": " + owner.name()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltip.add(Component.translatable("tooltip.starlight_logistics.activated_ender_pearl.unbound")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.starlight_logistics.activated_ender_pearl")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}