package icu.dreamripples.aero_suite.gravity.advancement;

import icu.dreamripples.aero_suite.gravity.GravityVisualization;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 集中持有并注册本 mod 的自定义成就触发器。
 *
 * 1.21.1 vanilla 把 CriterionTrigger 接入了 BuiltInRegistries.TRIGGER_TYPES
 * (Registry<CriterionTrigger<?>>),CriteriaTriggers.register(String,T) 内部走
 * Registry.register。但 built-in registry 在 bootstrap 后冻结,mod 构造器已晚于冻结,
 * 直接调用会抛 "Registry is already frozen"。改用 DeferredRegister 在 RegisterEvent
 * (冻结前) 注入条目,与注册方块/物品同构。
 *
 * JSON 中 "trigger": "gravity_visualization:spark_wand_kill" 由 CriteriaTriggers.CODEC
 * 从 TRIGGER_TYPES Registry 按 id 查找,DeferredRegister 注册后即可命中。
 */
public class ModTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(BuiltInRegistries.TRIGGER_TYPES, GravityVisualization.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, SparkWandKillTrigger> SPARK_WAND_KILL =
            TRIGGERS.register("spark_wand_kill", SparkWandKillTrigger::new);

    // 复用 SparkWandKillTrigger 类(matches 恒真),仅 ID 不同 -- 两个成就各自指向自己的 trigger ID。
    // CriteriaTrigger 实例 per-ID 维护 listener map,同 new 两个实例注册两个 ID 完全独立。
    public static final DeferredHolder<CriterionTrigger<?>, SparkWandKillTrigger> CREEPER_BUSTER_KILL =
            TRIGGERS.register("creeper_buster_kill", SparkWandKillTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, SparkWandKillTrigger> GOGGLE_OBSERVE =
            TRIGGERS.register("goggle_observe", SparkWandKillTrigger::new);

    // 打包自己成就: 含激活珍珠的包裹破裂并生成末影珍珠(玩家在线)时触发.
    // 触发点在 PackageEntityMixin 破裂分支(player != null), 语义已确保, matches 恒真.
    public static final DeferredHolder<CriterionTrigger<?>, SparkWandKillTrigger> PEARL_PACKAGE_TELEPORT =
            TRIGGERS.register("pearl_package_teleport", SparkWandKillTrigger::new);

    // 机械手载具抓取成就(Feature 17): 抓取会话确认 = "提起"(触发点 ExtendoGrabServer.tryStart
    // 末尾), 语义已确保, matches 恒真; 重型成就在触发点按质量 > 100 kpg 分流。
    public static final DeferredHolder<CriterionTrigger<?>, SparkWandKillTrigger> EXTENDO_GRAB_FIRST_LIFT =
            TRIGGERS.register("extendo_grab_first_lift", SparkWandKillTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, SparkWandKillTrigger> EXTENDO_GRAB_HEAVY_LIFT =
            TRIGGERS.register("extendo_grab_heavy_lift", SparkWandKillTrigger::new);
}
