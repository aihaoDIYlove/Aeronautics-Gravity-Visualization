package icu.dreamripples.aeronautics_gravity.enchantment;

import icu.dreamripples.aeronautics_gravity.AeronauticsGravityVisualization;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 1.21.1 的附魔系统是 data-driven:Java 端只持有 {@link ResourceKey<Enchantment>} 引用,
 * 附魔的实际定义(可附物品、效果、等级、成本)在 datapack JSON
 * (data/aeronautics_gravity/enchantment/*.json) 中。
 * 参考 Create 的 AllEnchantments.java + data/create/enchantment/*.json。
 */
public class ModEnchantments {
    public static final ResourceKey<Enchantment> CREEPER_BUSTER =
            ResourceKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.fromNamespaceAndPath(AeronauticsGravityVisualization.MOD_ID, "creeper_buster"));
}
