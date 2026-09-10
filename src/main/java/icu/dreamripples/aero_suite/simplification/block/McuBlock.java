package icu.dreamripples.aero_suite.simplification.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 单片机:可贴附在任意方块表面的装饰方块(无 BE 无逻辑)。
 * 双属性模式,属性结构同原版磨石 FaceAttachedHorizontalDirectionalBlock:
 * FACE(floor/wall/ceiling = 依附面)+ HORIZONTAL_FACING。
 * 注意不能用 BlockStateProperties.FACING + HORIZONTAL_FACING 组合——两者属性名都叫
 * "facing",同方块加两个同名属性会在注册期抛 duplicate property 崩溃。
 * 地面/天花板放置时丝印文字顶边指向玩家水平视线(comparator 式"正对玩家");
 * 贴墙时文字直立(字顶朝天花板)。blockstate 旋转约定:x=90 是 +Y→−Z(北),
 * 贴墙直立必须用 x=270 系(见 MOD_REFERENCE Feature 18)。
 * 碰撞箱只含芯片主体、不含引脚(引脚处可穿行)。依附方块失去牢固表面时破坏并掉落。
 * 模型/贴图来自 Blockbench 工程(Blockbench/单片机.bbmodel),顶面单独 96x128 高清贴图。
 */
public class McuBlock extends Block {

    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;

    // 芯片主体模型空间 x 2..14, y 0.5..2.5, z 0..16(/16);引脚不计入碰撞。
    // 地面/天花板的 12 宽边随 HORIZONTAL_FACING 换轴(H 南北 = 宽边沿 X):
    private static final VoxelShape SHAPE_UP_NS = Block.box(2, 0.5, 0, 14, 2.5, 16);
    private static final VoxelShape SHAPE_UP_EW = Block.box(0, 0.5, 2, 16, 2.5, 14);
    private static final VoxelShape SHAPE_DOWN_NS = Block.box(2, 13.5, 0, 14, 15.5, 16);
    private static final VoxelShape SHAPE_DOWN_EW = Block.box(0, 13.5, 2, 16, 15.5, 14);
    // 贴墙四向(芯片背贴依附面,碰撞箱与放置时玩家朝向无关):
    private static final VoxelShape SHAPE_NORTH = Block.box(2, 0, 13.5, 14, 16, 15.5);
    private static final VoxelShape SHAPE_SOUTH = Block.box(2, 0, 0.5, 14, 16, 2.5);
    private static final VoxelShape SHAPE_WEST = Block.box(13.5, 0, 2, 15.5, 16, 14);
    private static final VoxelShape SHAPE_EAST = Block.box(0.5, 0, 2, 2.5, 16, 14);

    public McuBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, HORIZONTAL_FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        AttachFace attach = face.getAxis() == Direction.Axis.Y
                ? (face == Direction.UP ? AttachFace.FLOOR : AttachFace.CEILING)
                : AttachFace.WALL;
        // 水平面点击: H = 玩家水平视线(文字顶边指向视线方向, comparator 式正对玩家);
        // 竖直面点击: H = 依附面本身(out 方向, 文字直立由 variant 表定死, 与玩家站位无关)。
        Direction horizontal = face.getAxis() == Direction.Axis.Y
                ? context.getHorizontalDirection()
                : face;
        return defaultBlockState().setValue(FACE, attach).setValue(HORIZONTAL_FACING, horizontal);
    }

    /** 依附面方向: FLOOR=上, CEILING=下, WALL=水平朝向(指向方块外)。 */
    protected static Direction getConnectedDirection(BlockState state) {
        return switch (state.getValue(FACE)) {
            case CEILING -> Direction.DOWN;
            case FLOOR -> Direction.UP;
            default -> state.getValue(HORIZONTAL_FACING);
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction connected = getConnectedDirection(state);
        BlockPos support = pos.relative(connected.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, connected);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (getConnectedDirection(state).getOpposite() == direction && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean wideX = state.getValue(HORIZONTAL_FACING).getAxis() == Direction.Axis.Z;
        return switch (state.getValue(FACE)) {
            case CEILING -> wideX ? SHAPE_DOWN_NS : SHAPE_DOWN_EW;
            case WALL -> switch (state.getValue(HORIZONTAL_FACING)) {
                case NORTH -> SHAPE_NORTH;
                case SOUTH -> SHAPE_SOUTH;
                case WEST -> SHAPE_WEST;
                default -> SHAPE_EAST;
            };
            default -> wideX ? SHAPE_UP_NS : SHAPE_UP_EW;
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        // 结构旋转只绕竖直轴: FACE(floor/wall/ceiling)不变, 水平朝向随之旋转
        return state.setValue(HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(HORIZONTAL_FACING, mirror.mirror(state.getValue(HORIZONTAL_FACING)));
    }
}
