package icu.dreamripples.aero_suite.common.registry;

import icu.dreamripples.aero_suite.starlight.StarlightLogistics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * 把本 mod 的物品插入到其它 mod 的创造页里,摆在语义相近的物品旁边。
 * <p>
 * 小堆锌粒 ({@code starlight_logistics:zinc_lump}) 是用 3× Create
 * {@code zinc_nugget} 合成的下游产物,放在 Create 的 {@code create:base} 页里、
 * 紧挨 {@code zinc_nugget} 之后更符合玩家直觉,而不是塞在本 mod 自己的页末。
 * <p>
 * 1.21.1 NeoForge 的 {@link BuildCreativeModeTabContentsEvent} 在 tab 自带的
 * {@code displayItems} generator 填充之后 fire {@link net.neoforged.bus.api.IModBusEvent},
 * 额外提供 {@code insertAfter(existingEntry, newEntry, visibility)}:可精确贴着锚点插入,
 * 但要求锚点 ItemStack 已在该 tab 中(底层 InsertableLinkedOpenCustomHashSet.addAfter,
 * 缺锚点会抛 IllegalArgumentException)。因此从 {@code getParentEntries()} 按
 * {@code getItem()} 捞出真身锚点再传回,避免凭空 new 锚点因组件/计数细微差异失配;
 * 锚点不在(例如 Create 改了龙肠/被 exclusion)则回退到普通 {@code accept} 末尾追加。
 * <p>
 * Create {@code zinc_nugget} 的 Registrate {@code ItemEntry} 不在本 mod compile
 * classpath,故经 {@link BuiltInRegistries#ITEM} 用注册名取 vanilla {@link Item} 引用。
 */
@EventBusSubscriber(modid = StarlightLogistics.MOD_ID)
public class CreativeTabInjections {

    /** Create 的 base 创造页 key ({@code AllCreativeModeTabs.BASE_CREATIVE_TAB}, id = {@code create:base})。 */
    public static final ResourceKey<CreativeModeTab> CREATE_BASE_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    ResourceLocation.fromNamespaceAndPath("create", "base"));

    @SubscribeEvent
    public static void buildCreateBaseTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CREATE_BASE_TAB)) return;

        Item zincNugget = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create", "zinc_nugget"));
        if (zincNugget == Items.AIR) { // Create 是硬依赖,走到这说明 Create 改了名
            icu.dreamripples.aero_suite.common.AeroSuite.LOGGER.warn(
                    "[AeroSuite] 找不到 create:zinc_nugget 锚点(Create 改名?), 小堆锌粒跳过创造页注入");
            return;
        }

        ItemStack lump = new ItemStack(ModItems.ZINC_LUMP.get());

        // 从已填充条目里捞真身锚点,缺锚点则回退到末尾追加(不抛异常)。
        ItemStack anchor = event.getParentEntries().stream()
                .filter(s -> s.getItem() == zincNugget)
                .findFirst()
                .orElse(null);

        if (anchor != null) {
            event.insertAfter(anchor, lump, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } else {
            event.accept(lump, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}