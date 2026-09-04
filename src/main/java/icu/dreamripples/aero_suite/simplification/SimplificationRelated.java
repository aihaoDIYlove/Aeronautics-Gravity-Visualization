package icu.dreamripples.aero_suite.simplification;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.stress.BlockStressValues;
import icu.dreamripples.aero_suite.common.AeroSuite;
import icu.dreamripples.aero_suite.common.AeroSuiteIds;
import icu.dreamripples.aero_suite.common.registry.ModBlocks;
import icu.dreamripples.aero_suite.common.registry.ModItems;
import icu.dreamripples.aero_suite.simplification.block.HangingDisplayRackBlock;
import icu.dreamripples.aero_suite.simplification.block.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * mod2 入口: 航空学：方便物品(simplification_related)。
 * 持有更方便的模拟传动器/变速式便携引擎/顺序供料器的注册 + 菜单类型。
 */
@Mod(SimplificationRelated.MOD_ID)
public class SimplificationRelated {
    public static final String MOD_ID = AeroSuiteIds.SIMPLIFICATION_ID;

    public SimplificationRelated(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        registerConfigScreen(container);
        ModItems.SIMPLIFICATION_ITEMS.register(modEventBus);
        ModBlocks.SIMPLIFICATION_BLOCKS.register(modEventBus);
        ModBlocks.SIMPLIFICATION_BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        modEventBus.addListener(SimplificationRelated::onCommonSetup);
        registerMovementChecks();
        AeroSuite.LOGGER.info("Aeronautics: Simplification Related loaded!");
    }

    // 悬挂展示架:向 Create 装配检查注册"脆性 + 朝支撑附着",与火把/告示牌同款待遇。
    // 必要性:Simulated/Sable 的物理化搜索(SimAssemblyContraption.moveBlock)直接查
    // BlockMovementChecks.isBlockAttachedTowards;而 Create 兜底 isMovementNecessaryFallback
    // 对"空碰撞箱"方块一律判 false —— 零质量测试夹具(故意 noCollission)若不注册,
    // 物理化时不会跟着支撑方块进 sublevel,且支撑被静默搬走不触发 updateShape,
    // 架子无声悬空、也不随支撑破坏而掉落(2026-09-04 测试反馈)。
    // brittle → isMovementNecessaryFallback 首行判 true,Create 原生机械动力装配同样带上。
    private static void registerMovementChecks() {
        BlockMovementChecks.registerBrittleCheck(state -> state.getBlock() instanceof HangingDisplayRackBlock
                ? BlockMovementChecks.CheckResult.SUCCESS
                : BlockMovementChecks.CheckResult.PASS);
        BlockMovementChecks.registerAttachedCheck((state, world, pos, direction) ->
                state.getBlock() instanceof HangingDisplayRackBlock
                        ? BlockMovementChecks.CheckResult.of(
                                state.getValue(HangingDisplayRackBlock.FACING).getOpposite() == direction)
                        : BlockMovementChecks.CheckResult.PASS);
    }

    // 变速式便携引擎:注册应力容量(超热 ×2 由 BE.calculateAddedStressCapacity 覆盖)
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (var holder : ModBlocks.VARIABLE_SPEED_PORTABLE_ENGINES.values()) {
                BlockStressValues.CAPACITIES.register(holder.get(), () -> 64.0);
            }
        });
    }

    // 三个 mod 的配置按钮都指向同一份自绘配置屏(读 gravity_visualization 的 COMMON 配置, 本地化见 AeroSuiteConfigScreen)
    private static void registerConfigScreen(net.neoforged.fml.ModContainer container) {
        // 注册走 client-only 的 ConfigScreenRegistrar: lambda 签名引用 vanilla Screen, 公共入口类
        // 在 DEDICATED_SERVER 上类校验即崩(运行期 if 守卫救不了), 必须物理隔离(同 mod1/mod3)
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            icu.dreamripples.aero_suite.common.client.ConfigScreenRegistrar.register(container);
        }
    }
}
