package icu.dreamripples.aeronautics_gravity.item;

import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import icu.dreamripples.aeronautics_gravity.fluid.ModFluids;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, AeronauticsGravityVisualization.MOD_ID);

    public static final Supplier<Item> SPARK_WAND =
            ITEMS.register("spark_wand", SparkWandItem::new);

    // 星空液体桶: 倒出后不蔓延(见 StarlightFluid), 只有一格 source.
    public static final Supplier<Item> STARLIGHT_BUCKET =
            ITEMS.register("starlight_bucket", () -> new BucketItem(
                    ModFluids.STARLIGHT.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    // 瓶装星空液体: 纯 Item(类比药水, 不走 Fluid). 在末地之海区域用玻璃瓶右键获得,
    // 取液交互见 StarlightBottleHandler. 也作合成/酿造材料.
    public static final Supplier<Item> STARLIGHT_BOTTLE =
            ITEMS.register("starlight_bottle", () -> new Item(
                    new Item.Properties().stacksTo(64).craftRemainder(Items.GLASS_BOTTLE)));

    // 自稳定方块(半成品框架): 纯物品(不可放置), sequenced_assembly 中间产物(transitional_item).
    // crafting 合成后, 经 Spout 6 次注液(星空液体) -> stabilizer(50%)/废品(50%). 参考 precision_mechanism.
    public static final Supplier<Item> INCOMPLETE_STABILIZER =
            ITEMS.register("incomplete_stabilizer", () -> new Item(new Item.Properties().stacksTo(64)));
}
