package icu.dreamripples.aero_suite.simplification.block;

import icu.dreamripples.aero_suite.simplification.SimplificationRelated;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本 mod 的菜单类型注册(首个)。用 IMenuTypeExtension.create 让 MenuType 携带
 * RegistryFriendlyByteBuf 工厂(openMenu 的 extraData -> createOnClient),
 * 等价 Create 的 Registrate MenuBuilder.createEntry(Registrate 是 Create jarjar,
 * 不在 compile classpath,不可用)。
 */
public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SimplificationRelated.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<SequentialFeederMenu>> SEQUENTIAL_FEEDER =
            MENUS.register("sequential_feeder", () ->
                    // 工厂不引用自身字段(javac 禁止字段初始化自引用);
                    // createMenu 调用方直接用 SEQUENTIAL_FEEDER.get() 传入 MenuType。
                    IMenuTypeExtension.create((windowId, inv, buf) ->
                            new SequentialFeederMenu(null, windowId, inv, buf)));

    // 单格漏斗: 普通 MenuType(无 extraData), 客户端用 (id, inv) 构造器 + 空容器占位
    public static final DeferredHolder<MenuType<?>, MenuType<SingleSlotHopperMenu>> SINGLE_SLOT_HOPPER =
            MENUS.register("single_slot_hopper", () ->
                    new MenuType<>(SingleSlotHopperMenu::new, FeatureFlags.VANILLA_SET));

}
