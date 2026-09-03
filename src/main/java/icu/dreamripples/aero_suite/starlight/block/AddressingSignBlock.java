package icu.dreamripples.aero_suite.starlight.block;

import com.mojang.serialization.MapCodec;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllTags.AllBlockTags;
import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 寻址牌:继承 {@link WallSignBlock}(自带"只能贴侧面"的 canSurvive + getStateForPlacement)。
 *
 * 关键覆写:
 * <ul>
 *   <li>{@link #newBlockEntity}:返回自定义 BE(避开 {@code SignBlock.newBlockEntity} 硬编码
 *       {@code BlockEntityType.SIGN} 的陷阱)。</li>
 *   <li>{@link #getDescriptionId}:返回固定语言键(避开 {@code WallSignBlock.getDescriptionId} 返回
 *       {@code asItem().getDescriptionId()} 在 BlockItem 注册时递归的陷阱)。</li>
 *   <li>{@link #getCollisionShape}:返回 empty(玩家可穿过);{@code getShape} 不覆写,继承
 *       {@code AABBS.get(FACING)} 供 raytrace 选取。</li>
 *   <li>{@link #useItemOn}:伪装板交互(Create copycat 同款)——非潜行右键持有效方块物品 → 贴材质
 *       (消耗 1 个,再右键同方块循环朝向属性);否则返回 {@code PASS_TO_DEFAULT_BLOCK_INTERACTION}
 *       拦截 SignApplicator(染料/墨囊/蜂巢),转 {@link #useWithoutItem}。潜行右键持方块仍开 GUI。</li>
 *   <li>{@link #useWithoutItem}:shift+右键打开 Create {@link ClipboardScreen} 编辑地址;普通右键无反应。</li>
 *   <li>{@link #onWrenched}:扳手拆下材质并返还消耗物品(潜行扳手 = 先拆材质再破坏方块,IWrenchable 默认)。</li>
 * </ul>
 *
 * 木牌渲染由 {@code AddressingSignRenderer} 接管(不画木牌,只画歌词式文字),故 block model 为空。
 */
public class AddressingSignBlock extends WallSignBlock implements IWrenchable {

    public static final MapCodec<AddressingSignBlock> CODEC = RecordCodecBuilder.mapCodec(
        p -> p.group(
            WoodType.CODEC.fieldOf("wood_type").forGetter(SignBlock::type),
            propertiesCodec()
        ).apply(p, AddressingSignBlock::new)
    );

    @Override
    public MapCodec<WallSignBlock> codec() {
        // WallSignBlock.codec() 已窄化为 MapCodec<WallSignBlock>(泛型不协变,子类无法继续窄化为
        // MapCodec<AddressingSignBlock>),故 cast CODEC。运行时安全:CODEC 实际构造 AddressingSignBlock 实例。
        return (MapCodec<WallSignBlock>) (MapCodec<?>) CODEC;
    }

    public AddressingSignBlock(WoodType type, BlockBehaviour.Properties properties) {
        super(type, properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AddressingSignBlockEntity(ModBlocks.ADDRESSING_SIGN_BE.get(), pos, state);
    }

    // 原版 SignBlock(BaseEntityBlock)是 getRenderShape=INVISIBLE 纯 BER 渲染,区块编译直接跳过,
    // 伪装板材质模型(AddressingSignCopycatModel)永远不被调用。改回 MODEL 走区块网格:
    // 无材质时模型为空仍透明(文字由 BER 照常画),贴材质后由裁剪 quads 渲染。
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public String getDescriptionId() {
        return "block.starlight_logistics.addressing_sign";
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level,
                                           BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 伪装板交互:非潜行 + 有效方块物品 → 贴材质;其余(SignApplicator 染料/墨囊/蜂巢等)走默认
        if (player != null && !player.isShiftKeyDown() && player.mayBuild()
                && level.getBlockEntity(pos) instanceof AddressingSignBlockEntity be) {
            BlockState accepted = getAcceptedBlockState(level, pos, stack, hitResult.getDirection());
            if (accepted != null) {
                // 客户端乐观返回(与 Create CopycatBlock 一致,真实写入由服务端 useItemOn 完成)
                if (level.isClientSide)
                    return ItemInteractionResult.SUCCESS;

                if (be.getMaterial().is(accepted.getBlock())) {
                    if (!be.cycleMaterial())
                        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .75f, .95f);
                    return ItemInteractionResult.SUCCESS;
                }
                if (be.hasCustomMaterial())
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

                be.setMaterial(accepted, stack);
                                level.playSound(null, pos, accepted.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1, .75f);
                if (!player.isCreative()) {
                    stack.shrink(1);
                    if (stack.isEmpty())
                        player.setItemInHand(hand, ItemStack.EMPTY);
                }
                return ItemInteractionResult.SUCCESS;
            }
        }
        // 拦截 SignApplicator(染料/发光墨囊/蜜脾),转 useWithoutItem
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * 材质接受判定(Create {@code CopycatBlock.getAcceptedBlockState} 移植):
     * BlockItem、非含 BE/楼梯;COPYCAT_ALLOW 白名单无条件放行,否则须 COPYCAT_DENY 不含且为
     * 整立方(选取箱满块 + 碰撞箱非空)。朝向类属性按所点击面对齐。
     */
    private BlockState getAcceptedBlockState(Level level, BlockPos pos, ItemStack item, Direction face) {
        if (!(item.getItem() instanceof BlockItem bi))
            return null;
        Block block = bi.getBlock();
        BlockState applied = block.defaultBlockState();
        if (!AllBlockTags.COPYCAT_ALLOW.matches(block)) {
            if (AllBlockTags.COPYCAT_DENY.matches(block))
                return null;
            if (block instanceof EntityBlock || block instanceof StairBlock)
                return null;
            VoxelShape shape = applied.getShape(level, pos);
            if (shape.isEmpty() || !shape.bounds().equals(Shapes.block().bounds()))
                return null;
            if (applied.getCollisionShape(level, pos).isEmpty())
                return null;
        }
        if (face != null) {
            Direction.Axis axis = face.getAxis();
            if (applied.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING))
                applied = applied.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, face);
            if (applied.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) && axis != Direction.Axis.Y)
                applied = applied.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, face);
            if (applied.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS))
                applied = applied.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, axis);
            if (applied.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS) && axis != Direction.Axis.Y)
                applied = applied.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS, axis);
        }
        return applied;
    }

    // 扳手:有材质则拆下并返还消耗物品;无材质 PASS。潜行扳手走 IWrenchable 默认(先本方法再破坏方块)。
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof AddressingSignBlockEntity be))
            return InteractionResult.PASS;
        if (!be.hasCustomMaterial())
            return InteractionResult.PASS;
        Player player = context.getPlayer();
        if (context.getLevel() instanceof ServerLevel serverLevel && player != null && !player.isCreative())
            player.getInventory().placeItemBackInInventory(be.getConsumedItem());
        context.getLevel().levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, context.getClickedPos(), Block.getId(be.getBlockState()));
        be.clearMaterial();
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        onWrenched(state, context);
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    // 破坏方块:掉落已消耗的材质物品(创造破坏由 playerWillDestroy 清空防双掉)
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.hasBlockEntity() || state.getBlock() == newState.getBlock())
            return;
        if (!isMoving && level.getBlockEntity(pos) instanceof AddressingSignBlockEntity be
                && be.hasCustomMaterial())
            Block.popResource(level, pos, be.getConsumedItem());
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        super.playerWillDestroy(level, pos, state, player);
        if (player.isCreative() && level.getBlockEntity(pos) instanceof AddressingSignBlockEntity be)
            be.clearMaterial();
        return state;
    }

    // ctrl+选取:贴了材质时优先给材质方块(潜行给寻址牌本身)。NeoForge IForgeBlock 签名(带 player)。
    @Override
    public ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target,
                                       LevelReader level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof AddressingSignBlockEntity be && be.hasCustomMaterial()
                && player != null && !player.isShiftKeyDown()) {
            return new ItemStack(be.getMaterial().getBlock());
        }
        return super.getCloneItemStack(state, target, level, pos, player);
    }


    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (player.isShiftKeyDown()
                && level.getBlockEntity(pos) instanceof AddressingSignBlockEntity be) {
            if (level.isClientSide) {
                openClipboardScreen(be.components(), pos, player);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @OnlyIn(Dist.CLIENT)
    private void openClipboardScreen(DataComponentMap components, BlockPos pos, Player player) {
        if (Minecraft.getInstance().player == player) {
            // targetSlot=0:targetedBlock != null 时 ClipboardEditPacket.handle 走 targetedBlock 分支,不用 targetSlot
            ScreenOpener.open(new ClipboardScreen(0, components, pos));
        }
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        // vanilla SignBlockEntity::tick + 自愈兜底(SignText 与选中地址脱节时 5 秒内自动重写)
        return createTickerHelper(blockEntityType, ModBlocks.ADDRESSING_SIGN_BE.get(),
                (lvl, pos, st, be) -> {
                    SignBlockEntity.tick(lvl, pos, st, be);
                    be.selfHealTick();
                });
    }
}
