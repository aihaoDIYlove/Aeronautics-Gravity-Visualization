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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
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
 *   <li>{@link #getCollisionShape}:返回与选取箱一致的墙牌薄板(=伪装板显示区域;Sable 以
 *       碰撞箱非空判质量,不可为空);{@code getShape} 不覆写,继承 {@code AABBS.get(FACING)}
 *       供 raytrace 选取。</li>
 *   <li>{@link #useItemOn}:伪装板交互(Create copycat 同款)——非潜行右键持有效方块物品 → 贴/换材质
 *       (不消耗物品;再右键同方块循环朝向属性);否则返回 {@code PASS_TO_DEFAULT_BLOCK_INTERACTION}
 *       拦截 SignApplicator(染料/墨囊/蜂巢),转 {@link #useWithoutItem}。潜行右键持方块仍开 GUI。</li>
 *   <li>{@link #useWithoutItem}:shift+右键打开 Create {@link ClipboardScreen} 编辑地址;普通右键无反应。</li>
 *   <li>{@link #onWrenched}:扳手拆下材质(材质不消耗,无需返还;潜行扳手 = 先拆材质再破坏方块,
 *       IWrenchable 默认)。</li>
 * </ul>
 *
 * 木牌渲染由 {@code AddressingSignRenderer} 接管(不画木牌,只画歌词式文字),故 block model 为空。
 */
public class AddressingSignBlock extends WallSignBlock implements IWrenchable {

    // 荧光墨囊点亮(glowing):亮度 8;blockstate json 的 variants 只按 facing 键,partial key 自动匹配
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty GLOWING =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("glowing");

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GLOWING);
    }

    // 亮度引擎只读 state(不能查 BE),发光态放 blockstate 属性(NeoForge IBlockExtension 签名)
    @Override
    public int getLightEmission(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return state.getValue(GLOWING) ? 8 : super.getLightEmission(state, level, pos);
    }

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
        // BooleanProperty 取值序为 [true, false],any() 默认落在 true——必须显式注册 false
        registerDefaultState(defaultBlockState().setValue(GLOWING, false));
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

    // 碰撞箱 = 显示区域:伪装板材质就是裁剪进选取箱(墙牌贴墙薄板 AABBS.get(FACING))渲染的,
    // 返回同款形状两者天然对齐,玩家撞到的是看得见的板而不是中心幽灵箱。
    // 同时满足 Sable 约束:碰撞箱不可为空——Sable 以"碰撞箱非空"判方块质量(getMass→isSolid,
    // 只看非空不看大小),空碰撞箱 = 零质量方块;物理化解除时逐块扣质量,总质量 ≤0 的瞬间
    // destroyAllBlocks 带掉落清空 plot,未清到的零质量方块会被连带掉落 → 刷物品(2026-09 踩坑)。
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level,
                                           BlockPos pos, CollisionContext context) {
        return state.getShape(level, pos, context);
    }

    // Sable 物理(装)解除逐块搬移方块:邻域更新/旋转中间态下支撑墙可能尚未落位,canSurvive=false
    // 会让寻址牌被判为无支撑而遭销毁(破坏音效+掉落),随后搬移又把牌子落回原位 → 刷物品。
    // 本体是悬浮装饰(失去支撑也不该消失),恒可存活;放置时的贴墙校验由 getStateForPlacement 自行完成。
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    // 复刻 vanilla WallSignBlock.getStateForPlacement,但支撑检查不再走恒真的 canSurvive
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = this.defaultBlockState();
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        LevelReader levelreader = context.getLevel();
        BlockPos blockpos = context.getClickedPos();

        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal()) {
                Direction facing = direction.getOpposite();
                blockstate = blockstate.setValue(FACING, facing);
                if (levelreader.getBlockState(blockpos.relative(facing.getOpposite())).isSolid()) {
                    return blockstate.setValue(WATERLOGGED, Boolean.valueOf(fluidstate.getType() == Fluids.WATER));
                }
            }
        }
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 荧光墨囊:切换发光方块态(亮度 8),消耗 1 个(原版 GLOW_INK_SAC_USE 音效)
        // 其余 SignApplicator(染料染色/蜜脾涂蜡)透传原版 SignBlock.useItemOn——自带 isWaxed 门控与音效
        // 斧头:涂蜡的寻址牌除蜡(原版蜡质方块语义)
        if (player != null && !player.isShiftKeyDown() && player.mayBuild()) {
            if (stack.getItem() instanceof net.minecraft.world.item.GlowInkSacItem
                    && level.getBlockEntity(pos) instanceof AddressingSignBlockEntity beGlow) {
                // 涂蜡后封锁发光切换(与染料同语义)
                if (beGlow.isWaxed()) {
                    if (level instanceof ServerLevel serverLevel)
                        serverLevel.playSound(null, pos, beGlow.getSignInteractionFailedSoundEvent(), SoundSource.BLOCKS);
                    return ItemInteractionResult.SUCCESS;
                }
                if (!level.isClientSide) {
                    boolean next = !state.getValue(GLOWING);
                    level.setBlock(pos, state.setValue(GLOWING, next), 3);
                    level.playSound(null, pos, SoundEvents.GLOW_INK_SAC_USE, SoundSource.BLOCKS, 1f, 1f);
                    if (!player.isCreative())
                        stack.shrink(1);
                }
                return ItemInteractionResult.SUCCESS;
            }
            if (stack.getItem() instanceof net.minecraft.world.item.SignApplicator)
                return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
            if (stack.getItem() instanceof net.minecraft.world.item.AxeItem
                    && level.getBlockEntity(pos) instanceof AddressingSignBlockEntity beWax && beWax.isWaxed()) {
                if (level instanceof ServerLevel serverLevel) {
                    beWax.setWaxed(false);
                    serverLevel.levelEvent(LevelEvent.PARTICLES_WAX_OFF, pos, 0);
                    serverLevel.playSound(null, pos, SoundEvents.AXE_WAX_OFF, SoundSource.BLOCKS, 1f, 1f);
                }
                return ItemInteractionResult.SUCCESS;
            }
        }
        // 伪装板交互:非潜行 + 有效方块物品 → 贴材质;其余(SignApplicator 染料/墨囊/蜂巢等)走默认
        if (player != null && !player.isShiftKeyDown() && player.mayBuild()
                && level.getBlockEntity(pos) instanceof AddressingSignBlockEntity be) {
            BlockState accepted = getAcceptedBlockState(level, pos, stack, hitResult.getDirection());
            if (accepted != null && be.isWaxed()) {
                // 涂蜡后冻结纹理(贴/换/同方块循环全部封锁,与染料同语义)
                if (level instanceof ServerLevel serverLevel)
                    serverLevel.playSound(null, pos, be.getSignInteractionFailedSoundEvent(), SoundSource.BLOCKS);
                return ItemInteractionResult.SUCCESS;
            }
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
                // 不消耗物品:直接贴上/覆盖已有材质(地址数据在 BE,不受影响)
                be.setMaterial(accepted);
                level.playSound(null, pos, accepted.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1, .75f);
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

    // 扳手:有材质则拆下材质(物品不返还,材质本就不消耗);无材质 PASS。涂蜡后封锁(失败音效)。
    // 潜行扳手走 IWrenchable 默认(先本方法再破坏方块——破坏不受涂蜡影响,只锁纹理重置)。
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof AddressingSignBlockEntity be))
            return InteractionResult.PASS;
        if (!be.hasCustomMaterial())
            return InteractionResult.PASS;
        if (be.isWaxed()) {
            if (context.getLevel() instanceof ServerLevel serverLevel)
                serverLevel.playSound(null, context.getClickedPos(),
                        be.getSignInteractionFailedSoundEvent(), SoundSource.BLOCKS);
            return InteractionResult.SUCCESS;
        }
        context.getLevel().levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, context.getClickedPos(), Block.getId(be.getBlockState()));
        be.clearMaterial();
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        onWrenched(state, context);
        return IWrenchable.super.onSneakWrenched(state, context);
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
            // 涂蜡后封锁编辑(原版语义:失败音效 + SUCCESS,不开 GUI)
            if (be.isWaxed()) {
                if (!level.isClientSide)
                    level.playSound(null, pos, be.getSignInteractionFailedSoundEvent(), SoundSource.BLOCKS);
                return InteractionResult.SUCCESS;
            }
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
