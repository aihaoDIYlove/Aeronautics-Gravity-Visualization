package icu.dreamripples.aero_suite.gravity.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.entities.diagram.DiagramConfig;
import dev.simulated_team.simulated.content.entities.diagram.DiagramEntity;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 便携图解的客户端逻辑(打开 Simulated 机器图纸页面)。
 * 独立成类使 common 侧的 PortableDiagramItem 不直接引用任何客户端类(Minecraft/
 * ClientSubLevel/DiagramScreen),由 isClientSide 分支调用,规避专用服务器
 * NoClassDefFoundError 隐患。
 *
 * 工作原理(与原内联实现一致):
 * - 右键命中的方块面决定图纸初始观察视角,复用 Simulated 的 face -> yRot/xRot 转换。
 * - 构造「伪」DiagramEntity(不 addFreshEntity、不存档、不渲染)仅作 DiagramScreen 句柄。
 * - DiagramScreen.open 复用 Simulated 整套 GUI/渲染/数据包链路,数据刷新走
 *   RequestDiagramDataPacket(subLevelUUID),不依赖实体;Screen 内调整角度发
 *   DiagramSaveConfigPacket(entityId=-1) 服务端 instanceof 失败直接 return,无害废包。
 */
public final class PortableDiagramClient {

    private PortableDiagramClient() {}

    public static void useOnClient(UseOnContext context) {
        Level clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null) return;

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
}
