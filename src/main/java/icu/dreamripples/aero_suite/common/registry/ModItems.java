package icu.dreamripples.aero_suite.common.registry;

import icu.dreamripples.aero_suite.common.AeroSuiteIds;
import icu.dreamripples.aero_suite.gravity.item.PortableDiagramItem;
import icu.dreamripples.aero_suite.gravity.item.SparkWandItem;
import icu.dreamripples.aero_suite.starlight.fluid.ModFluids;
import icu.dreamripples.aero_suite.starlight.item.ActivatedEnderPearlItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 三个 mod 的物品注册,按 owner 拆三套 DeferredRegister(物品 id 的 ns = 归属 mod)。
 * BlockItem 也走 ITEM registry,所以各方块的 BlockItem 注册到方块所属 mod 的 ITEMS 注册器
 * (见 ModBlocks);本类只集中持有三个注册器 + 纯物品(非 BlockItem)。
 */
public final class ModItems {

    // ── mod1: gravity_visualization ──────────────────────────────
    public static final DeferredRegister<Item> GRAVITY_ITEMS =
            DeferredRegister.create(Registries.ITEM, AeroSuiteIds.GRAVITY_ID);

    public static final Supplier<Item> SPARK_WAND =
            GRAVITY_ITEMS.register("spark_wand", SparkWandItem::new);

    // 便携图解: 右键载具直接打开 Simulated 机器图纸页面, 免去放置 contraption_diagram 实体到墙面.
    // 初始视角由命中的方块面决定(复用 Simulated face->yRot/xRot 转换). 贴图暂复用 simulated:contraption_diagram.
    public static final Supplier<Item> PORTABLE_DIAGRAM =
            GRAVITY_ITEMS.register("portable_diagram", PortableDiagramItem::new);

    // 配重块(半成品): 序列装配中间产物, 贴图复用 block/counterweight.png.
    // 安山机壳 + 2 轮机械手安装(工业铁块/油门拉杆) -> 配重块(100%).
    public static final Supplier<Item> INCOMPLETE_COUNTERWEIGHT =
            GRAVITY_ITEMS.register("incomplete_counterweight", () -> new Item(new Item.Properties().stacksTo(64)));

    // 配"轻"块(半成品): 序列装配中间产物, 贴图复用 block/counterweight_light.png.
    // 配重块 + 4 轮注液 1000mb 浮空混合物 -> 配"轻"块(100%).
    public static final Supplier<Item> INCOMPLETE_COUNTERWEIGHT_LIGHT =
            GRAVITY_ITEMS.register("incomplete_counterweight_light", () -> new Item(new Item.Properties().stacksTo(64)));

    // ── mod2: simplification_related ─────────────────────────────
    public static final DeferredRegister<Item> SIMPLIFICATION_ITEMS =
            DeferredRegister.create(Registries.ITEM, AeroSuiteIds.SIMPLIFICATION_ID);

    // 顺序供料器(半成品): 序列装配中间产物, 贴图复用 block/sequential_feeder.png.
    // 安山机壳 + 4 步机械手安装(列表过滤器/红石/安山漏斗/木桶) -> 顺序供料器(100%).
    public static final Supplier<Item> INCOMPLETE_SEQUENTIAL_FEEDER =
            SIMPLIFICATION_ITEMS.register("incomplete_sequential_feeder", () -> new Item(new Item.Properties().stacksTo(64)));

    // ── mod3: starlight_logistics ────────────────────────────────
    public static final DeferredRegister<Item> STARLIGHT_ITEMS =
            DeferredRegister.create(Registries.ITEM, AeroSuiteIds.STARLIGHT_ID);

    // 星空液体桶: 倒出后不蔓延(见 StarlightFluid), 只有一格 source.
    public static final Supplier<Item> STARLIGHT_BUCKET =
            STARLIGHT_ITEMS.register("starlight_bucket", () -> new BucketItem(
                    ModFluids.STARLIGHT.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    // 瓶装星空液体: 纯 Item(类比药水, 不走 Fluid). 在末地之海区域用玻璃瓶右键获得,
    // 取液交互见 StarlightBottleHandler. 也作合成/酿造材料.
    public static final Supplier<Item> STARLIGHT_BOTTLE =
            STARLIGHT_ITEMS.register("starlight_bottle", () -> new Item(
                    new Item.Properties().stacksTo(64).craftRemainder(Items.GLASS_BOTTLE)));

    // 世界锚点框架: 纯物品(不可放置), 世界锚点的序列装配中间产物.
    public static final Supplier<Item> INCOMPLETE_WORLD_ANCHOR =
            STARLIGHT_ITEMS.register("incomplete_world_anchor", () -> new Item(new Item.Properties().stacksTo(64)));

    // 自稳定方块(半成品): 序列装配中间产物, 贴图复用 block/stabilizer.png.
    // starlight_casing + 3 步机械手安装(红石配重/姿态传感器/红石配轻) -> stabilizer(100%).
    public static final Supplier<Item> INCOMPLETE_STABILIZER =
            STARLIGHT_ITEMS.register("incomplete_stabilizer", () -> new Item(new Item.Properties().stacksTo(64)));

    // 浮空水晶(半成品): 序列装配中间产物, 贴图复用 aeronautics:block/levitite.
    // 超轻玻璃 + 注液 500mb 浮空混合物 + 注液 500mb 星空液体 -> 浮空水晶(100%).
    public static final Supplier<Item> INCOMPLETE_LEVITITE =
            STARLIGHT_ITEMS.register("incomplete_levitite", () -> new Item(new Item.Properties().stacksTo(64)));

    // 小堆锌粒: 3 锌粒无序合成, 注液 1000mb 星空液体 -> 海晶沙砾.
    public static final Supplier<Item> ZINC_LUMP =
            STARLIGHT_ITEMS.register("zinc_lump", () -> new Item(new Item.Properties().stacksTo(64)));

    // 被激活的末影珍珠: 玩家主手 ender_pearl + 副手 echo_shard 右键合成(见 ActivatedEnderPearlHandler),
    // 携带玩家 UUID(见 ModDataComponents.ACTIVATED_PEARL_OWNER). 装入 Create Package 待物流派送,
    // 包裹作为实体静止 3 秒后破裂 -> 生成一颗末影珍珠实体 -> 落地传送玩家到落点(跨维度由 vanilla
    // ThrownEnderpearl.onHit 自带). stacksTo(16) 与 vanilla ender_pearl 一致.
    public static final Supplier<Item> ACTIVATED_ENDER_PEARL =
            STARLIGHT_ITEMS.register("activated_ender_pearl",
                    () -> new ActivatedEnderPearlItem(new Item.Properties().stacksTo(16)));

    private ModItems() {}
}
