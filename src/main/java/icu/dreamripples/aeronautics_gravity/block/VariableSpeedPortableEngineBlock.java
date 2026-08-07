package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.foundation.utility.BlockHelper;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlock;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import dev.simulated_team.simulated.service.SimItemService;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 变速式便携引擎方块 - 继承 Simulated 便携引擎,16 色变种(同原版 DyedBlockList 配色)。
 * 仅重定向 BE 到自注册类型 + 覆盖染色交互指向本 mod 色表(原版 useItemOn 染色指向
 * SimBlocks.PORTABLE_ENGINES,会染回原版便携引擎)。燃料/蛋糕/创造蛋糕等交互复用 super。
 */
public class VariableSpeedPortableEngineBlock extends PortableEngineBlock {

    public VariableSpeedPortableEngineBlock(Properties properties, DyeColor color) {
        super(properties, color);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<PortableEngineBlockEntity> getBlockEntityClass() {
        return (Class<PortableEngineBlockEntity>) (Class<?>) VariableSpeedPortableEngineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PortableEngineBlockEntity> getBlockEntityType() {
        return ModBlocks.VARIABLE_SPEED_PORTABLE_ENGINE_BE.get();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // 仅当新方块不是变速版便携引擎(被拆除/替换为其他方块)时才掉落燃料并移除 BE。
        // 必须覆盖:父类用 SimBlocks.PORTABLE_ENGINES.contains 判断,我们的方块不在该列表,
        // 导致 LIT 变化(tick 里 setBlock(LIT))误触发 drop+removeBE,燃料被吐出、BE 重建、speedSetting 重置。
        if (state.hasBlockEntity() && !(newState.getBlock() instanceof VariableSpeedPortableEngineBlock)) {
            PortableEngineBlockEntity be = (PortableEngineBlockEntity) level.getBlockEntity(pos);
            if (be != null && !be.inventory.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.inventory.getItem(0));
            }
            level.removeBlockEntity(pos);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState blockState, Level level, BlockPos blockPos,
                                              Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        // 拦截染料:替换成本 mod 的变速版色表(而非原版便携引擎)
        DyeColor color = SimItemService.getDyeColor(heldItem);
        if (color != null) {
            if (!level.isClientSide) {
                level.playSound(null, blockPos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.1f - level.random.nextFloat() * .2f);
                BlockState newState = BlockHelper.copyProperties(blockState,
                        ModBlocks.VARIABLE_SPEED_PORTABLE_ENGINES.get(color).get().defaultBlockState());
                level.setBlockAndUpdate(blockPos, newState);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(heldItem, blockState, level, blockPos, player, interactionHand, blockHitResult);
    }
}
