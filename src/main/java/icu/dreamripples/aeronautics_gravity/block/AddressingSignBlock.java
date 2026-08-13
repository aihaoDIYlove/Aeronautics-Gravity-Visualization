package icu.dreamripples.aeronautics_gravity.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
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
 *   <li>{@link #useItemOn}:返回 {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} 拦截 SignApplicator
 *       (染料/墨囊/蜂巢),转 {@link #useWithoutItem}。</li>
 *   <li>{@link #useWithoutItem}:shift+右键打开 Create {@link ClipboardScreen} 编辑地址;普通右键无反应。</li>
 * </ul>
 *
 * 木牌渲染由 {@code AddressingSignRenderer} 接管(不画木牌,只画歌词式文字),故 block model 为空。
 */
public class AddressingSignBlock extends WallSignBlock {

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

    @Override
    public String getDescriptionId() {
        return "block.aeronautics_gravity.addressing_sign";
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level,
                                           BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 拦截 SignApplicator(染料/发光墨囊/蜂巢),转 useWithoutItem
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
        return createTickerHelper(blockEntityType, ModBlocks.ADDRESSING_SIGN_BE.get(), SignBlockEntity::tick);
    }
}
