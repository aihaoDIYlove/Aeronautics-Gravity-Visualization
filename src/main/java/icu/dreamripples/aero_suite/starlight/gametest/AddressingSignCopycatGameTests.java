package icu.dreamripples.aero_suite.starlight.gametest;

import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import icu.dreamripples.aero_suite.gravity.GravityVisualization;
import icu.dreamripples.aero_suite.starlight.block.AddressingSignBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * 寻址牌伪装板服务端交互 gametest:
 * 放置寻址牌 → 模拟玩家非潜行右键持石头 → 断言材质贴上且不消耗物品;
 * 再验证已有材质时可直接换方块;最后 clearMaterial 回到 AIR。
 */
@GameTestHolder(GravityVisualization.MOD_ID)
public class AddressingSignCopycatGameTests {

    private static void use(BlockState state, ServerLevel level, Player player,
                            ItemStack stack, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
        state.useItemOn(stack, level, player, InteractionHand.MAIN_HAND, hit);
    }

    @GameTest(template = "copycat_test")
    public static void copycatApplyAndReplace(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ADDRESSING_SIGN_BLOCK.get().defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        BlockState state = helper.getBlockState(pos);

        // 1. 非潜行右键持石头 → 贴材质,不消耗物品
        ItemStack stone = new ItemStack(Items.STONE, 16);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        use(state, level, player, stone, abs);

        if (!(helper.getBlockEntity(pos) instanceof AddressingSignBlockEntity be)) {
            helper.fail("寻址牌 BE 不存在");
            return;
        }
        if (!be.hasCustomMaterial())
            helper.fail("材质未贴上(服务端 useItemOn 未生效)");
        if (!be.getMaterial().is(Blocks.STONE))
            helper.fail("材质方块错误: " + be.getMaterial());
        if (stone.getCount() != 16)
            helper.fail("不应消耗物品: count=" + stone.getCount());

        // 2. 已贴材质时持另一种有效方块(圆石)→ 直接替换
        ItemStack cobble = new ItemStack(Items.COBBLESTONE, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, cobble);
        use(helper.getBlockState(pos), level, player, cobble, abs);
        if (!be.getMaterial().is(Blocks.COBBLESTONE))
            helper.fail("已有材质时应可直接换材质: " + be.getMaterial());
        if (cobble.getCount() != 4)
            helper.fail("换材质不应消耗物品");

        // 3. 拆除(clearMaterial,扳手路径的落点)→ 回到 AIR
        be.clearMaterial();
        if (be.hasCustomMaterial())
            helper.fail("clearMaterial 未清空材质");

        helper.succeed();
    }
}
