package icu.dreamripples.aero_suite.simplification.client;

import com.simibubi.create.AllSpecialTextures;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import icu.dreamripples.aero_suite.simplification.block.FilteredHopperSlotPositioning;
import icu.dreamripples.aero_suite.simplification.block.FilteredSingleSlotHopperBlock;
import icu.dreamripples.aero_suite.simplification.block.FilteredSingleSlotHopperBlockEntity;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 过滤漏斗悬停白框: 准星命中过滤漏斗时, 在所看面的标记槽位置画一个描边框(白框),
 * 命中框内时叠加 {@code THIN_CHECKERED} 面贴图提示"可点击"。镜像 Create
 * {@code FilteringRenderer.tick}(去掉 count/ValueSettings 部分, 本方块无数量设置)。
 *
 * <p>潜行时不显示(同 Create {@code ValueSettingsInputHandler.canInteract} 门槛, 让玩家
 * 潜行右键直接开 GUI 绕过标记交互)。空过滤也显示框(提示"点击设置")。
 */
@OnlyIn(Dist.CLIENT)
public final class FilteredHopperOutline {

    private FilteredHopperOutline() {}

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;
        if (mc.player.isShiftKeyDown())
            return; // 潜行: 隐藏框, 让 vanilla 开 GUI
        HitResult target = mc.hitResult;
        if (!(target instanceof BlockHitResult hit))
            return;
        if (hit.getType() != HitResult.Type.BLOCK)
            return;
        BlockPos pos = hit.getBlockPos();
        Level level = mc.level;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FilteredSingleSlotHopperBlock))
            return;
        if (!(level.getBlockEntity(pos) instanceof FilteredSingleSlotHopperBlockEntity be))
            return;

        Direction side = hit.getDirection();
        FilteredHopperSlotPositioning positioning = new FilteredHopperSlotPositioning();
        positioning.fromSide(side);
        if (!positioning.shouldRender(level, pos, state))
            return; // 上下面不显示(isSideActive 限定), shouldRender 内部含该判定

        // 命中判定(框内还是框外)
        Vec3 localHit = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        boolean hitBox = positioning.testHit(level, pos, state, localHit);

        ItemStack filter = be.getFilter();
        boolean isFilterCard = filter.getItem() instanceof FilterItem;
        // 框尺寸同 Create: 过滤卡大框, 普通物品小框
        AABB bb = isFilterCard
                ? new AABB(Vec3.ZERO, Vec3.ZERO).inflate(0.45f, 0.31f, 0.2f)
                : new AABB(Vec3.ZERO, Vec3.ZERO).inflate(0.25f);

        Component label = Component.translatable("block.simplification_related.filtered_single_slot_hopper");
        ValueBox box = new ValueBox.ItemValueBox(label, bb, pos, filter, Component.empty());
        // 框外或手持扳手 -> passive(暗淡、非交互态); 框内 -> active(高亮)
        box.passive(!hitBox || mc.player.getMainHandItem().getItem() instanceof WrenchItem);
        box.transform(positioning);

        Outliner.getInstance()
                .showOutline(Pair.of("filtered_hopper_filter", pos), box)
                .lineWidth(1 / 64f)
                .withFaceTexture(hitBox ? AllSpecialTextures.THIN_CHECKERED : null)
                .highlightFace(side);
    }
}
