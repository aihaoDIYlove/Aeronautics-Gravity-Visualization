package icu.dreamripples.aero_suite.simplification.block;

import com.simibubi.create.foundation.block.IBE;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * 悬挂展示架(测试物件): 可贴在方块<b>任意面</b>上的纯透明 8x8x1 薄板, 右键存取单件物品
 * (交互语义照搬珍珠滞留台 {@code PearlStasisBlock}: 恒 count=1, 无 GUI)。
 *
 * <p><b>这是给 Sable 零质量刷物品 bug 的复现夹具, 有意为之, 勿"修"</b>:
 * 注册属性带 {@code .noCollission()} → 碰撞箱为空 → Sable {@code isSolid}=false →
 * 质量 0 → 物理化解除时 {@code destroyAllBlocks} 带掉落, 即复现刷物品
 * (机制详见寻址牌 F13 的 Sable 零质量条目; 珍珠滞留台/寻址牌是修掉的对照组)。
 *
 * <ul>
 *   <li><b>只能贴在方块上</b>: {@code FACING} = 离开支撑面的方向(= 放置时所点击的面),
 *       {@link #canSurvive} 要求 {@code FACING.getOpposite()} 方向是实心方块; 失去支撑经
 *       {@link #updateShape} 自动破坏(掉落自身 + 释放槽内物品)。</li>
 *   <li><b>无物理碰撞</b>: noCollission, 玩家可穿过。</li>
 *   <li><b>纯透明</b>: block model 空(同寻址牌的空模型手法), 槽内物品由
 *       {@code HangingDisplayRackRenderer} 渲染在薄板外侧(沿 FACING 偏移)。</li>
 *   <li>无合成配方, 仅创造栏(mod2 段)获取。</li>
 * </ul>
 */
public class HangingDisplayRackBlock extends Block implements IBE<HangingDisplayRackBlockEntity> {

    /** 离开支撑面的方向: rack 在 pos, 支撑方块在 pos.relative(FACING.getOpposite())。 */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** 8x8x1 薄板贴着支撑面; key = FACING(离开支撑的方向)。 */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.DOWN, Block.box(4.0, 15.0, 4.0, 12.0, 16.0, 12.0),
            Direction.UP, Block.box(4.0, 0.0, 4.0, 12.0, 1.0, 12.0),
            Direction.NORTH, Block.box(4.0, 4.0, 15.0, 12.0, 12.0, 16.0),
            Direction.SOUTH, Block.box(4.0, 4.0, 0.0, 12.0, 12.0, 1.0),
            Direction.WEST, Block.box(15.0, 4.0, 4.0, 16.0, 12.0, 12.0),
            Direction.EAST, Block.box(0.0, 4.0, 4.0, 1.0, 12.0, 12.0));

    public HangingDisplayRackBlock(Properties properties) {
        super(properties);
        // 显式默认 DOWN(保持"贴天花板下"的初始语义; BlockStateProperties.FACING 取值序首位也是 DOWN)
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).isSolid();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // FACING = 所点击的面(支撑面朝向 rack 的反方向), 贴哪面由点击面决定
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    // 支撑方块(贴附面)被移除时自动破坏(updateShape 的 direction = 被改动邻块的方向)
    @Override
    protected BlockState updateShape(BlockState state, Direction direction,
                                     BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public Class<HangingDisplayRackBlockEntity> getBlockEntityClass() {
        return HangingDisplayRackBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HangingDisplayRackBlockEntity> getBlockEntityType() {
        return ModBlocks.HANGING_DISPLAY_RACK_BE.get();
    }

    // 持物右键: 槽空则存入 1 个(创造不消耗); 其余情况透传默认交互
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, net.minecraft.world.InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof HangingDisplayRackBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.isEmpty() || !be.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide()) {
            be.setHeld(stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.SUCCESS;
    }

    // 空手右键: 取出槽内物品
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof HangingDisplayRackBlockEntity be) || be.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            ItemStack taken = be.getHeld().copy();
            be.setHeld(ItemStack.EMPTY);
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false);
            }
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    // 破坏/被覆盖时释放槽内物品(不释放会被吞)
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof HangingDisplayRackBlockEntity be && !be.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, be.getHeld());
            be.setHeld(ItemStack.EMPTY);
        }
        IBE.onRemove(state, level, pos, newState);
    }
}
