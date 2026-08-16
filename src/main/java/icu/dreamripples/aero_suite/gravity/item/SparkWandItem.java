package icu.dreamripples.aero_suite.gravity.item;

import dev.ryanhcode.sable.Sable;
import icu.dreamripples.aero_suite.gravity.ModEnchantments;
import icu.dreamripples.aero_suite.gravity.advancement.ModTriggers;
import icu.dreamripples.aero_suite.gravity.client.MassVisualizer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import icu.dreamripples.aero_suite.common.AeronauticsGravityVisualization;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

public class SparkWandItem extends Item {
    public static final int MAX_DURABILITY = 64;
    public static final float FIRE_DAMAGE = 4.0F;

    public SparkWandItem() {
        super(new Item.Properties().stacksTo(1).durability(MAX_DURABILITY));
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getEnchantmentValue() {
        // 让火花魔杖可在附魔台附魔(默认 0 = 不可附魔)。返回 36。
        // 1.21.1 此方法被 @Deprecated(附魔系统 data-driven 重构),但尚无 Item.Properties 替代方法
        // (.enchantable 是更高版本才引入),只能重写 + @SuppressWarnings。参考 PlungerLauncherItem:181。
        return 36;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isEnchantable(ItemStack stack) {
        // 显式允许 durability 物品进附魔台(参考 BacktankItem#isEnchantable)。
        return true;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        // 白名单:苦力怕克星、亡灵杀手、耐久、击退、抢夺、经验修补。
        // 两层检查机制:第一层 Enchantment.supportedItems (tag 检查,在附魔台/EnchantmentHelper) 由
        // data/minecraft/tags/item/enchantable/{weapon,sword,durability}.json 让 spark_wand 进 tag 通过;
        // 第二层本方法精确过滤掉同 tag 的非白名单附魔(锋利/虫灾/火焰附加/横扫等)。
        if (enchantment.is(ModEnchantments.CREEPER_BUSTER)
                || enchantment.is(Enchantments.SMITE)
                || enchantment.is(Enchantments.UNBREAKING)
                || enchantment.is(Enchantments.KNOCKBACK)
                || enchantment.is(Enchantments.LOOTING)
                || enchantment.is(Enchantments.MENDING)) {
            return true;
        }
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            var clientLevel = Minecraft.getInstance().level;
            if (clientLevel == null) return InteractionResult.SUCCESS;

            var hitPos = context.getClickedPos();

            // 通过 Sable 的 HELPER 直接查找瞄准位置所在的 SubLevel
            SubLevel subLevel = Sable.HELPER.getContaining(clientLevel, hitPos);

            AeronauticsGravityVisualization.LOGGER.info(
                    "[SparkWand] useOn at {}, subLevel={}",
                    hitPos, subLevel);

            if (subLevel instanceof ClientSubLevel cs) {
                boolean heavy = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
                MassVisualizer.toggle(cs, heavy);
                AeronauticsGravityVisualization.LOGGER.info("[SparkWand] => toggled (heavy={})!", heavy);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // 附加 4 点"燃烧伤害"标签的伤害,但不让生物真的燃烧起来:
        // damageSources().onFire() 基于 vanilla 的 on_fire 伤害类型,该类型 effects=burning,
        // 所以会被 IS_FIRE 标签包含(抗火药水免疫、死亡消息"被烧死"、屏幕灼烧特效),
        // 但 LivingEntity.hurt 路径不会调用 setSecondsOnFire —— 生物不会持续起火。
        // 节肢生物怕火:对 EntityTypeTags.ARTHROPOD(蜘蛛/洞穴蜘蛛/蠹虫/末影螨/蜜蜂等)造成
        // 双倍燃烧伤害(8 点)。沿用 vanilla 虫灾附魔(Bane of Arthropods)的同款 tag 判定。
        if (!target.level().isClientSide()) {
            boolean arthropod = target.getType().is(EntityTypeTags.ARTHROPOD);
            float damage = arthropod ? FIRE_DAMAGE * 2.0F : FIRE_DAMAGE;
            // 致命一击判定:Minecraft 掉生肉/熟肉取决于实体 isOnFire() 状态(由 setSecondsOnFire
            // 设置),而非伤害类型。普通攻击已扣血,此时 getHealth() <= damage 说明加上
            // 本次燃烧伤害会致死 -- 仅在这种情况下提前点火 1 秒,让 die() -> dropAllDeathLoot 检测到
            // isOnFire=true 从而走 furnace_smelt 掉熟肉。非致命攻击不点火,避免日常攻击污染视觉。
            if (target.getHealth() <= damage) {
                target.igniteForSeconds(1);
            }
            target.hurt(target.damageSources().onFire(), damage);
            // 在生物身上播放火花粒子(原版火把同款 FLAME 粒子),3-6 个围绕身体中心随机散布。
            // sendParticles 的 xOffset/yOffset/zOffset 是位置散布范围,speed=0 让粒子原地生成后
            // 靠 FLAME 粒子自身 tick 缓慢上浮消散,视觉上像火花从生物身上冒出。仅服务端发送,
            // 客户端自动接收粒子包渲染(普通世界路径,非 SubLevel,addParticle/sendParticles 可靠)。
            if (target.level() instanceof ServerLevel serverLevel) {
                int count = 3 + serverLevel.random.nextInt(4);
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        count, 0.3, 0.4, 0.3, 0.0);
            }
            // 击杀判定:普攻与附加火伤都已结算,hurtEnemy 由 Player.attack 在普攻 hurt 之后调用,
            // 火伤在本方法内造成 -- 两条致死路径在此处汇合,isDeadOrDying() 都能命中。火伤走 onFire()
            // 且 causing entity 为 null,无法被 vanilla player_killed_entity 匹配,故用自定义触发器兜底。
            if (target.isDeadOrDying() && attacker instanceof ServerPlayer serverPlayer) {
                // 点燃爬虫:用火花魔杖击杀节肢生物
                if (arthropod) {
                    ModTriggers.SPARK_WAND_KILL.get().trigger(serverPlayer);
                }
                // 别碰我的机器!:附魔苦力怕克星秒杀苦力怕(creeper_buster 对苦力怕 +100 伤害,满血 20 必杀,
                // 故"持有该附魔 + 击杀苦力怕"即等价秒杀)。
                if (target.getType() == EntityType.CREEPER
                        && stack.getEnchantmentLevel(target.level().holderOrThrow(ModEnchantments.CREEPER_BUSTER)) > 0) {
                    ModTriggers.CREEPER_BUSTER_KILL.get().trigger(serverPlayer);
                }
            }
        }
        // 左键攻击消耗 1 点耐久。1.21.1 的 hurtAndBreak 用 EquipmentSlot 重载
        // (见 _research/Simulated-Project/.../LevititeCatalystCrystallizationPacket.java)
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_gravity.spark_wand.full").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_gravity.spark_wand.heavy").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_gravity.spark_wand.goggles").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.aeronautics_gravity.spark_wand.fire").withStyle(ChatFormatting.GOLD));
    }
}
