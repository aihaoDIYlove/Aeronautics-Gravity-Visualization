package icu.dreamripples.aero_suite.starlight.event;

import icu.dreamripples.aero_suite.common.config.AeroSuiteConfig;
import icu.dreamripples.aero_suite.common.config.FeatureGates;
import icu.dreamripples.aero_suite.starlight.StarlightLogistics;
import icu.dreamripples.aero_suite.starlight.component.ModDataComponents;
import icu.dreamripples.aero_suite.starlight.network.VoidTouchHighlightPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 虚空之触连锁采集: 持有 {@link ModDataComponents#VOID_TOUCH} 标记的镐子 **Shift+右键** 方块进入
 * 预览/确认两段交互(服务端权威); 普通右键完全放行, 不影响拉杆/活板门等 vanilla 交互:
 * <ul>
 *   <li><b>Shift+右键</b>: 计算连锁集合, 下发客户端高亮(3 秒无动作/切换物品自动取消);
 *       对同一目标再 Shift+右键 = 确认挖掘, 服务端重算后逐格破坏。</li>
 *   <li><b>Shift+滚轮</b>: 调整连锁上限挡位 1/2/4/8/16/32/48/64(默认 16, 上增下减, 存玩家数据)。</li>
 * </ul>
 * 破坏分两条路径:
 * <ul>
 *   <li><b>普通方块</b>(含矿石/草丛): 模拟玩家挖掘(BreakEvent -> mineBlock 耗耐久受 Unbreaking ->
 *       getDrops 带时运/精准采集 -> getExpDrop 经验 -> spawnAfterBreak), 算法抄自 Caps 连锁挖矿
 *       ({@code common/VeinMine.collect})。掉落/经验统一在起点结算(不散落)。</li>
 *   <li><b>末地特殊方块</b>(传送门框架/末地传送门/末地折跃门): hardness=-1 或无 loot 表,
 *       特判路径: air 替换 + 末影粒子 + 固定掉落(无眼框架/1 空间碎片/4 空间碎片)。
 *       固定扣耐久(直接改 damageValue, 无视 Unbreaking)。</li>
 * </ul>
 * 耐久门槛(随挡位联动): 特殊方块 = 特殊消耗+1; 普通方块 = max(特殊消耗+1, 挡位数, 整链不中断);
 * 草丛(0 耗耐久)只需 1 点。连锁中途剩余 < 2 时兜底中断。保证最坏情况镐子剩 1 点不碎。
 */
@EventBusSubscriber(modid = StarlightLogistics.MOD_ID)
public final class VoidTouchHandler {

    /** 预览(armed)有效期: 3 秒无动作自动取消。 */
    private static final int ARMED_TTL_TICKS = 3 * 20;

    /** Shift+滚轮挡位表: 1/2/4/8/16/32/48/64, 默认 16(下标 4)。 */
    private static final int[] TIERS = {1, 2, 4, 8, 16, 32, 48, 64};
    private static final int DEFAULT_TIER_INDEX = 4;
    /** 挡位存玩家 persistentData 的 key(仿 Caps 的 vein_mine_max, 跨存档保留)。 */
    private static final String TIER_KEY = "aero_suite_void_touch_limit";

    /** 已进入"预览待确认"状态的连锁目标(服务端权威, 客户端高亮只是展示)。 */
    private record Armed(BlockPos pos, long expiry) {}

    private static final Map<UUID, Armed> ARMED = new HashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack tool = event.getItemStack();
        if (!hasVoidTouch(tool)) return;
        if (!FeatureGates.isEnabled("void_touch")) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!isVoidTouchTarget(state)) return; // 无效目标: 放行 vanilla 行为
        // 只有潜行右键会被虚空之触接管(预览/确认), 事件在此之后才 cancel;
        // 非潜行右键完全放行: 拉杆/活板门/门等 vanilla 交互不受影响

        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return; // 非潜行右键完全放行: 拉杆/活板门/门等 vanilla 交互不受影响
        SpecialDrop special = SpecialDrop.of(state);
        boolean grass = special == null && isGrass(state.getBlock());
        int limit = getChainLimit(player, grass);
        int specialCost = tuning(FeatureGates.CONFIG, c -> c.tuning.specialDurabilityCost.get(), 16);
        boolean creative = player.getAbilities().instabuild;

        // 耐久门槛(随挡位联动): 特殊方块 = 特殊消耗+1; 普通方块 = max(特殊消耗+1, 挡位数, 整链不中断);
        // 草丛(0 耗耐久)只要还有 1 点就能用。
        int gate = special != null ? specialCost + 1
                : grass ? 1
                : Math.max(specialCost + 1, limit);
        if (!creative && tool.getMaxDamage() - tool.getDamageValue() < gate) {
            if (!level.isClientSide())
                player.displayClientMessage(Component.translatable("aero_suite.void_touch.no_durability"), true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        if (level.isClientSide()) {
            // 客户端只做占位取消, 实际计算/破坏全在服务端(高亮由服务端 S2C payload 下发)
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer sp = (ServerPlayer) player;
        long now = level.getGameTime();
        Armed armed = ARMED.get(player.getUUID());
        boolean same = armed != null && armed.pos().equals(pos) && now <= armed.expiry();

        if (same) {
            // 已在预览态: 再潜行右键 = 确认挖掘
            ARMED.remove(player.getUUID());
            sendHighlight(sp, List.of());
            if (special != null) {
                harvestSpecial(serverLevel, sp, tool, state, pos, specialCost, special);
            } else {
                executeChain(serverLevel, sp, tool, pos, limit);
            }
            return;
        }

        // 新预览: 计算连锁集合下发高亮, 6 秒后自动过期
        ARMED.put(player.getUUID(), new Armed(pos, now + ARMED_TTL_TICKS));
        List<BlockPos> preview = special != null ? List.of(pos) : computeChain(serverLevel, pos, limit);
        sendHighlight(sp, preview);
        player.displayClientMessage(
                Component.translatable("aero_suite.void_touch.armed", preview.size()), true);
    }

    /** 预览期间主手切换物品 -> 自动取消(客户端高亮同步清除)。每 10 tick 巡检一次 armed 玩家。 */
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (ARMED.isEmpty() || event.getEntity().level().getGameTime() % 10 != 0) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Armed armed = ARMED.get(sp.getUUID());
        if (armed == null) return;
        long now = sp.serverLevel().getGameTime();
        boolean expired = now > armed.expiry();
        boolean switched = !hasVoidTouch(sp.getMainHandItem());
        if (expired || switched) {
            ARMED.remove(sp.getUUID());
            if (switched || expired) sendHighlight(sp, List.of());
        }
    }

    /** Shift+滚轮调挡位(C2S): 邻近移动挡位表并 clamp, 存 persistentData; 若在预览态则实时刷新高亮。 */
    public static void onAdjustLimit(ServerPlayer sp, int delta) {
        if (!FeatureGates.isEnabled("void_touch")) return;
        int index = Math.max(0, Math.min(TIERS.length - 1, tierIndex(sp) + (delta >= 0 ? 1 : -1)));
        sp.getPersistentData().putInt(TIER_KEY, TIERS[index]);
        sp.displayClientMessage(
                Component.translatable("aero_suite.void_touch.limit", TIERS[index]), true);

        Armed armed = ARMED.get(sp.getUUID());
        if (armed != null && sp.serverLevel().getGameTime() <= armed.expiry()) {
            BlockState state = sp.serverLevel().getBlockState(armed.pos());
            List<BlockPos> preview = SpecialDrop.of(state) != null ? List.of(armed.pos())
                    : computeChain(sp.serverLevel(), armed.pos(), TIERS[index]);
            sendHighlight(sp, preview);
        }
    }

    // ── 挡位 ──────────────────────────────────────────────────────

    private static int tierIndex(Player player) {
        int limit = player.getPersistentData().getInt(TIER_KEY);
        for (int i = 0; i < TIERS.length; i++)
            if (TIERS[i] == limit) return i;
        return DEFAULT_TIER_INDEX; // 未设置/非法值 -> 默认 16
    }

    /** 实际连锁上限 = min(玩家挡位, 该类型方块的上限)。 */
    private static int getChainLimit(Player player, boolean grass) {
        int tier = TIERS[tierIndex(player)];
        int cap = grass
                ? tuning(FeatureGates.CONFIG, c -> c.tuning.grassChainLimit.get(), 64)
                : tuning(FeatureGates.CONFIG, c -> c.tuning.chainLimit.get(), 16);
        return Math.min(tier, cap);
    }

    // ── 连锁计算 ──────────────────────────────────────────────────
    // 算法抄自 Caps 连锁挖矿(common/VeinMine.collect): 完整 3×3×3 立方邻域(含斜向连接)。
    // 注意不能用 BlockPos.withinManhattan -- 它是十字形(曼哈顿距离)扩展, 斜向矿脉连不上,
    // 会导致高亮/破坏范围与预期不符(此前的 bug)。

    /** BFS 同类型方块; 草丛用 grassReach 立方半径(隔空格可连), 其余 1(3×3×3 邻域含斜连)。 */
    private static List<BlockPos> computeChain(ServerLevel level, BlockPos origin, int limit) {
        Block target = level.getBlockState(origin).getBlock();
        boolean grass = isGrass(target);
        int reach = grass ? tuning(FeatureGates.CONFIG, c -> c.tuning.grassReach.get(), 3) : 1;

        List<BlockPos> result = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        java.util.HashSet<BlockPos> visited = new java.util.HashSet<>();
        queue.add(origin);
        visited.add(origin);
        result.add(origin);
        outer:
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (int dx = -reach; dx <= reach; dx++)
                for (int dy = -reach; dy <= reach; dy++)
                    for (int dz = -reach; dz <= reach; dz++) {
                        if (result.size() >= limit) break outer;
                        BlockPos neighbor = current.offset(dx, dy, dz);
                        if (neighbor.equals(current) || !visited.add(neighbor)) continue;
                        if (level.getBlockState(neighbor).getBlock() != target) continue;
                        queue.add(neighbor.immutable());
                        result.add(neighbor.immutable());
                    }
        }
        return result;
    }

    // ── 连锁破坏(普通路径, 照 BlockHelper.destroyBlockAs) ─────────

    private static void executeChain(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos origin, int limit) {
        List<BlockPos> chain = computeChain(level, origin, limit); // 服务端重算, 不信任过期的高亮列表
        int totalXp = 0;
        List<ItemStack> drops = new ArrayList<>(); // 攒齐后统一在起点掉出(抄 Caps), 避免到处散落
        for (BlockPos pos : chain) {
            Integer xp = breakAsPlayer(level, player, tool, pos, drops);
            if (xp == null) break;
            totalXp += xp;
            // 链中耐久保底: 剩余 < 2 停止(入口已有门槛, 这里只防长链中途挖爆)
            if (!player.getAbilities().instabuild && tool.getMaxDamage() - tool.getDamageValue() < 2) break;
        }
        for (ItemStack drop : drops) Block.popResource(level, origin, drop);
        if (totalXp > 0) ExperienceOrb.award(level, origin.getCenter(), totalXp);
    }

    /**
     * 模拟玩家挖掘单格。返回该格经验(被外部取消时返回 null, 终止剩余连锁);
     * 掉落物不就地弹出, 加入 dropsOut 由 executeChain 统一在起点结算。
     * 经验用 NeoForge 的 getExpDrop(时运/精准采集正确)。
     */
    private static Integer breakAsPlayer(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos pos,
                                         List<ItemStack> dropsOut) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || SpecialDrop.of(state) != null) return 0;
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, tool)) return 0;
        // 工具挖不动的方块(错误工具类型)不纳入: 不白耗耐久(抄 Caps 的 hasCorrectToolForDrops 门槛)
        if (!player.isCreative() && !player.hasCorrectToolForDrops(state, level, pos)) return null;

        BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(level, pos, state, player);
        NeoForge.EVENT_BUS.post(breakEvent);
        if (breakEvent.isCanceled()) return null;

        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        boolean creative = player.getAbilities().instabuild;

        // 耐久: mineBlock 内部走 hurtAndBreak(受 Unbreaking 概率减免); 破坏速度 0 的方块(草丛)自动免耗
        tool.mineBlock(level, state, pos, player);
        player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
        player.causeFoodExhaustion(0.025f); // 与 vanilla 挖掘一致(抄 Caps)

        int xp = 0;
        if (level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS) && !creative) {
            BlockDropsEvent dropsEvent = new BlockDropsEvent(level, pos, state, blockEntity,
                    new ArrayList<>(), player, tool);
            NeoForge.EVENT_BUS.post(dropsEvent);
            xp = state.getExpDrop(level, pos, blockEntity, player, tool);
            if (!dropsEvent.isCanceled())
                // getDrops 传入 player + 手持工具: 时运/精准采集/其它 loot 修饰全走 vanilla loot 表
                dropsOut.addAll(Block.getDrops(state, level, pos, blockEntity, player, tool));
            state.spawnAfterBreak(level, pos, ItemStack.EMPTY, false);
        }

        FluidState fluidState = level.getFluidState(pos);
        level.setBlockAndUpdate(pos, fluidState.createLegacyBlock());
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(state));
        return xp;
    }

    // ── 末地特殊方块采集(特判路径) ─────────────────────────────────

    private enum SpecialDrop {
        FRAME(() -> new ItemStack(Items.END_PORTAL_FRAME)),
        SHARD1(() -> new ItemStack(icu.dreamripples.aero_suite.common.registry.ModItems.SPACE_SHARD.get())),
        SHARD4(() -> {
            ItemStack stack = new ItemStack(icu.dreamripples.aero_suite.common.registry.ModItems.SPACE_SHARD.get());
            stack.setCount(4);
            return stack;
        });

        private final java.util.function.Supplier<ItemStack> drop;

        SpecialDrop(java.util.function.Supplier<ItemStack> drop) { this.drop = drop; }

        ItemStack get() { return drop.get(); }

        static SpecialDrop of(BlockState state) {
            Block block = state.getBlock();
            if (block == Blocks.END_PORTAL_FRAME) return FRAME;
            if (block == Blocks.END_PORTAL) return SHARD1;
            if (block == Blocks.END_GATEWAY) return SHARD4;
            return null;
        }
    }

    private static void harvestSpecial(ServerLevel level, ServerPlayer player, ItemStack tool,
                                       BlockState state, BlockPos pos, int cost, SpecialDrop special) {
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, tool)) return;

        state.spawnAfterBreak(level, pos, ItemStack.EMPTY, false);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        Block.popResource(level, pos, special.get());

        // 末影粒子 + 破坏音效(setBlock 静默替换, 补反馈)
        level.sendParticles(ParticleTypes.PORTAL,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
        player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(state));

        // 固定扣 16 点, 不走 hurtAndBreak(否则 Unbreaking 会 roll 掉概率, 不是"固定消耗")
        // 入口门槛保证扣完至少剩 1 点, 镐子不会碎
        if (!player.getAbilities().instabuild)
            tool.setDamageValue(Math.min(tool.getDamageValue() + cost, tool.getMaxDamage() - 1));
    }

    // ── 工具函数 ──────────────────────────────────────────────────

    private static boolean hasVoidTouch(ItemStack stack) {
        return stack.has(ModDataComponents.VOID_TOUCH.get())
                && stack.get(ModDataComponents.VOID_TOUCH.get()) != null;
    }

    private static boolean isGrass(Block block) {
        return block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS;
    }

    /** 可作为虚空之触目标: 末地特殊方块 / 草丛 / 普通可破坏方块(排除基岩类 hardness=-1 与一切带 BE 的方块)。 */
    private static boolean isVoidTouchTarget(BlockState state) {
        if (state.isAir()) return false;
        if (SpecialDrop.of(state) != null) return true; // 白名单在前: 末地折跃门虽带 BE 仍可采集
        if (state.hasBlockEntity()) return false; // 箱子/熔炉/刷怪笼等容器与功能方块不连锁
        if (isGrass(state.getBlock())) return true;
        // getDestroySpeed(null, ...) 隔离第三方方块对 level 参数的怪异依赖
        try {
            return state.getDestroySpeed(null, BlockPos.ZERO) >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void sendHighlight(ServerPlayer sp, List<BlockPos> positions) {
        PacketDistributor.sendToPlayer(sp, new VoidTouchHighlightPayload(positions));
    }

    private static <T> T tuning(AeroSuiteConfig config, java.util.function.Function<AeroSuiteConfig, T> getter, T fallback) {
        return config != null ? getter.apply(config) : fallback;
    }

    private VoidTouchHandler() {}
}
