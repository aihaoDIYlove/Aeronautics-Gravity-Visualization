package icu.dreamripples.aero_suite.gravity.item;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.entities.diagram.DiagramConfig;
import dev.simulated_team.simulated.content.entities.diagram.DiagramEntity;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 便携图解: 右键载具直接打开 Simulated 的「机器图纸」页面, 免去放置 contraption_diagram 实体到墙面的麻烦.
 *
 * 工作原理:
 * - 玩家右键命中的载具方块面 (clickedFace) 决定图纸的初始观察视角, 复用 Simulated 的
 *   face -> yRot/xRot 转换逻辑 (DiagramEntity 构造器内部 updateFacingWithBoundingBox + DiagramConfig.makeDefault).
 * - 在客户端构造一个「伪」DiagramEntity (用 DiagramEntity(Level, BlockPos, Direction, Direction) 构造器,
 *   它内部硬编码取 SimEntityTypes.CONTRAPTION_DIAGRAM), 仅作 DiagramScreen 的句柄 + 提供 yRot/xRot.
 *   该实体不 addFreshEntity, 不进世界、不存档、不渲染, 生命周期等同本次方法调用.
 * - 调 DiagramScreen.open(fake, config, clientSubLevel) 直接复用 Simulated 整套 GUI/渲染/数据包链路.
 *   数据刷新走 RequestDiagramDataPacket(subLevelUUID) — 不依赖实体, 自动工作.
 * - 不持久化: config 始终用 makeDefault 重建; Screen 内调整角度发 DiagramSaveConfigPacket(entityId=-1),
 *   服务端 level.getEntity(-1) 返回 null, 处理器 instanceof 失败直接 return, 无害废包.
 */
public class PortableDiagramItem extends Item {

    public PortableDiagramItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            Level clientLevel = Minecraft.getInstance().level;
            if (clientLevel == null) return InteractionResult.SUCCESS;

            BlockPos hitPos = context.getClickedPos();
            SubLevel subLevel = Sable.HELPER.getContaining(clientLevel, hitPos);

            if (subLevel instanceof ClientSubLevel cs) {
                // 命中的面 -> 初始观察视角. 复用 DiagramItem 的 verticalOrientation 选择规则:
                // 水平面(贴墙) 用 DOWN 作竖直朝向; 垂直面(贴顶/底) 用玩家水平朝向.
                Direction face = context.getClickedFace();
                Direction verticalOrientation = face.getAxis().isHorizontal()
                        ? Direction.DOWN
                        : context.getHorizontalDirection();

                // 伪实体: pos 用 BlockPos.ZERO (不存档不渲染, 仅作 Screen 句柄).
                DiagramEntity fake = new DiagramEntity(clientLevel, BlockPos.ZERO, face, verticalOrientation);
                DiagramConfig config = DiagramConfig.makeDefault(fake);

                DiagramScreen.open(fake, config, cs);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gravity_visualization.portable_diagram")
                .withStyle(ChatFormatting.GRAY));
    }
}