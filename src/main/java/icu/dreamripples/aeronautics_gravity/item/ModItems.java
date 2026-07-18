package icu.dreamripples.aeronautics_gravity.item;

import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, AeronauticsGravityVisualization.MOD_ID);

    public static final Supplier<Item> SPARK_WAND =
            ITEMS.register("spark_wand", SparkWandItem::new);
}
