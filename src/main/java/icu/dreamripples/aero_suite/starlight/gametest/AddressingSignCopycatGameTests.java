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

    // 染料/蜜脾/斧头走原版 SignApplicator 通道:染料染色(不消耗在 wax 前提下消耗染料)、蜜脾涂蜡、
    // 涂蜡后染料失效、斧头除蜡、荧光墨囊被吞不掉落。染色面可能是正面或背面(mock 玩家朝向不定),两 face 都接受。
    @GameTest(template = "copycat_test")
    public static void copycatDyeWax(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ADDRESSING_SIGN_BLOCK.get().defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        BlockState state = helper.getBlockState(pos);

        if (!(helper.getBlockEntity(pos) instanceof AddressingSignBlockEntity be)) {
            helper.fail("寻址牌 BE 不存在");
            return;
        }

        // 0. 原版 canApplyToSign 要求牌面有文字(空牌不可染色),两面各写一行
        be.setText(new net.minecraft.world.level.block.entity.SignText()
                .setMessage(0, net.minecraft.network.chat.Component.literal("a")), true);
        be.setText(new net.minecraft.world.level.block.entity.SignText()
                .setMessage(0, net.minecraft.network.chat.Component.literal("a")), false);

        // 1. 红色染料 → 染色成功 + 消耗 1 个,未涂蜡
        ItemStack dye = new ItemStack(Items.RED_DYE, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, dye);
        use(state, level, player, dye, abs);
        if (be.getText(true).getColor() != net.minecraft.world.item.DyeColor.RED
                && be.getText(false).getColor() != net.minecraft.world.item.DyeColor.RED)
            helper.fail("染料未染色: " + be.getText(true).getColor());
        if (dye.getCount() != 3)
            helper.fail("染料未消耗: count=" + dye.getCount());
        if (be.isWaxed())
            helper.fail("初始不应为涂蜡状态");

        // 2. 荧光墨囊 → 点亮为发光方块(亮度 8)+ 消耗 1 个;再点一次熄灭回 0
        ItemStack glow = new ItemStack(Items.GLOW_INK_SAC, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, glow);
        use(state, level, player, glow, abs);
        BlockState glowState = helper.getBlockState(pos);
        if (glowState.getValue(ModBlocks.ADDRESSING_SIGN_BLOCK.get().GLOWING) != Boolean.TRUE)
            helper.fail("荧光墨囊未点亮发光态");
        if (ModBlocks.ADDRESSING_SIGN_BLOCK.get().getLightEmission(glowState, level, abs) != 8)
            helper.fail("发光态亮度不是 8");
        if (glow.getCount() != 1)
            helper.fail("荧光墨囊未消耗: count=" + glow.getCount());
        use(helper.getBlockState(pos), level, player, glow, abs);
        BlockState offState = helper.getBlockState(pos);
        if (offState.getValue(ModBlocks.ADDRESSING_SIGN_BLOCK.get().GLOWING) != Boolean.FALSE
                || ModBlocks.ADDRESSING_SIGN_BLOCK.get().getLightEmission(offState, level, abs) != 0)
            helper.fail("二次点击未熄灭发光态");

        // 3. 蜜脾 → 涂蜡 + 消耗 1 个
        ItemStack honeycomb = new ItemStack(Items.HONEYCOMB, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, honeycomb);
        use(state, level, player, honeycomb, abs);
        if (!be.isWaxed())
            helper.fail("蜜脾未涂蜡");
        if (honeycomb.getCount() != 1)
            helper.fail("蜜脾未消耗: count=" + honeycomb.getCount());

        // 4. 涂蜡后再染色 → 原版门控拒绝,颜色不变
        ItemStack dye2 = new ItemStack(Items.BLUE_DYE, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, dye2);
        use(state, level, player, dye2, abs);
        if (be.getText(true).getColor() == net.minecraft.world.item.DyeColor.BLUE
                || be.getText(false).getColor() == net.minecraft.world.item.DyeColor.BLUE)
            helper.fail("涂蜡后染料不应生效");
        if (dye2.getCount() != 4)
            helper.fail("涂蜡后染料不应消耗");

        // 5. 斧头 → 除蜡
        ItemStack axe = new ItemStack(Items.IRON_AXE, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        use(state, level, player, axe, abs);
        if (be.isWaxed())
            helper.fail("斧头未除蜡");

        // 6. 支撑墙消失 → 牌子不消失(canSurvive 恒真,物理搬移不被判死)
        BlockState signState = helper.getBlockState(pos);
        helper.setBlock(abs.relative(signState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).getOpposite()),
                Blocks.AIR.defaultBlockState());
        if (!helper.getBlockState(pos).getBlock().equals(ModBlocks.ADDRESSING_SIGN_BLOCK.get()))
            helper.fail("失去支撑后寻址牌被销毁");

        helper.succeed();
    }
}
