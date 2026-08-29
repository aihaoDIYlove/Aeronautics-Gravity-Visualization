package icu.dreamripples.aero_suite.simplification.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.logistics.filter.FilterItem;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 带过滤的单格漏斗方块: 外观/红石/朝向/漏斗逻辑全部继承原版 {@link HopperBlock}(与
 * {@link SingleSlotHopperBlock} 同源), 仅替换 BE 类型并接管"右键标记槽"交互。
 *
 * <p><b>标记交互</b>(仿 Create 智能溜槽/黄铜漏斗, 见 {@code ValueSettingsInputHandler.
 * onBlockActivated} + {@code FilteringBehaviour.onShortInteract}):
 * <ul>
 *   <li>准星命中<b>侧面标记框区域</b>(4 横向面, {@link FilteredHopperSlotPositioning}
 *       的 testHit 判定)时: 持物 -> 设过滤标记; 空手 -> 清除标记。消耗规则同 Create --
 *       Create 过滤卡({@link FilterItem})放入时<b>消耗手中 1 个</b>, 旧过滤卡退还玩家;
 *       普通物品作标记是<b>幽灵</b>(不消耗, 仅记录种类)。</li>
 *   <li>命中框外, 或玩家潜行/旁观/手持扳手 -> 放行 vanilla 行为(空手/别处右键 -> 开 GUI)。</li>
 * </ul>
 *
 * <p>过滤判定本身在 BE 的 {@code canPlaceItem} 收口(见 {@link FilteredSingleSlotHopperBlockEntity}),
 * 本方块只管"标记怎么设", 不管"物品怎么过滤"。
 */
public class FilteredSingleSlotHopperBlock extends HopperBlock implements IWrenchable {
    public static final MapCodec<HopperBlock> CODEC = simpleCodec(FilteredSingleSlotHopperBlock::new);

    public FilteredSingleSlotHopperBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.simplification_related.filtered_single_slot_hopper")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public MapCodec<HopperBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FilteredSingleSlotHopperBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 基类 createTickerHelper 对比 BlockEntityType.HOPPER, 必须换成自己的类型
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlocks.FILTERED_SINGLE_SLOT_HOPPER_BE.get(),
                        (BlockEntityTicker<HopperBlockEntity>) HopperBlockEntity::pushItemsTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // 槽内物品由 vanilla HopperBlock.onRemove 的 dropContentsOnDestroy 自动掉落;
        // 但过滤标记不在 Container 里, Create 过滤卡是真实物品(放入时已消耗 1 个),
        // 破坏时必须弹出避免丢失。普通物品标记是幽灵(从未消耗), 不弹出(防刷)。
        if (!level.isClientSide && !isMoving && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof FilteredSingleSlotHopperBlockEntity be) {
            ItemStack filter = be.getFilter();
            if (filter.getItem() instanceof FilterItem)
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), filter.copy());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // ---- 标记交互路由 ----

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        InteractionResult r = tryFilterInteract(state, level, pos, player, hand, hitResult);
        if (r != null)
            return r.consumesAction() ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        InteractionResult r = tryFilterInteract(state, level, pos, player, InteractionHand.MAIN_HAND, hitResult);
        if (r != null)
            return r;
        // 命中框外 -> vanilla 开 GUI(useWithoutItem 即漏斗开菜单的入口)
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    /**
     * 命中侧面标记框 -> 设/清过滤, 返回 {@link InteractionResult#SUCCESS};
     * 否则返回 {@code null} 表示放行 vanilla。
     *
     * <p>门槛同 Create {@code ValueSettingsInputHandler.canInteract}: 旁观/潜行跳过;
     * 手持扳手跳过(让扳手走 vanilla, 不误设过滤)。
     */
    @Nullable
    private static InteractionResult tryFilterInteract(BlockState state, Level level, BlockPos pos, Player player,
                                                       InteractionHand hand, BlockHitResult hitResult) {
        if (player.isSpectator() || player.isShiftKeyDown())
            return null;
        if (player.getMainHandItem().getItem() instanceof WrenchItem)
            return null;
        if (!(level.getBlockEntity(pos) instanceof FilteredSingleSlotHopperBlockEntity be))
            return null;

        Direction side = hitResult.getDirection();
        FilteredHopperSlotPositioning positioning = new FilteredHopperSlotPositioning();
        positioning.fromSide(side);
        if (!positioning.shouldRender(level, pos, state))
            return null; // 仅 4 横向面(isSideActive 限定), shouldRender 内部含该判定
        Vec3 localHit = hitResult.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        if (!positioning.testHit(level, pos, state, localHit))
            return null; // 没命中框区域

        ItemStack held = player.getItemInHand(hand);

        // 客户端: 仅消费点击(防 GUI 打开预测), 不写(filter 写是服务端权威)
        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        // 旧过滤若是 Create 过滤卡 -> 退还玩家(普通物品标记是幽灵, 无需退)
        ItemStack oldFilter = be.getFilter();
        if (oldFilter.getItem() instanceof FilterItem && !player.isCreative())
            player.getInventory().placeItemBackInInventory(oldFilter.copy());

        // 设新过滤(空手 = 清除)。幽灵标记统一 count=1:count 无语义但会随 saveOptional
        // 存盘/渲染,保留原堆数(如 64)只会造成视觉与存档噪音
        ItemStack toApply = held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1);
        be.setFilter(toApply);

        // Create 过滤卡放入消耗 1 个; 普通物品标记不消耗(幽灵)
        if (!held.isEmpty() && held.getItem() instanceof FilterItem && !player.isCreative()) {
            if (held.getCount() <= 1)
                player.setItemInHand(hand, ItemStack.EMPTY);
            else
                held.shrink(1);
        }

        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.25f, 0.1f);
        return InteractionResult.SUCCESS;
    }
}
