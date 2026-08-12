package icu.dreamripples.aeronautics_gravity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import icu.dreamripples.aeronautics_gravity.block.GlowSignBlock;
import icu.dreamripples.aeronautics_gravity.block.GlowSignBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 发光告示牌 BER:不画木牌(纯透明),只画歌词式 4 行文字。
 *
 * 朝向复刻 {@code SignRenderer.translateSign} 的 wall 分支:translate(0.5, 0.75*scale, 0.5) +
 * mulPose(YP, -yRot) + wall extra translate(0, -0.3125, -0.4375)。文字 scale = 0.015625 * 0.6666667,
 * TEXT_OFFSET = (0, 0.33333334, 0.046666667),lineHeight = 10,4 行居中 j = 20(复刻 SignRenderer 常量)。
 *
 * 第 2 行(displayLine=1) = 选中地址:白色 + 发光({@code drawInBatch8xOutline},满光 15728880)。
 * 其余 3 行 = 上下文 [sel-1, sel+1, sel+2]:灰色({@code drawInBatch} + POLYGON_OFFSET 防 z-fighting)。
 * 空列表时第 2 行显示 "shift+右键" 灰色提示。只画正面(不 mulPose 180)。
 */
@OnlyIn(Dist.CLIENT)
public class GlowSignRenderer implements BlockEntityRenderer<GlowSignBlockEntity> {

    private static final float RENDER_SCALE = 0.6666667F;
    private static final float TEXT_OFFSET_Y = 0.33333334F;
    private static final float TEXT_OFFSET_Z = 0.046666667F;
    private static final int LINE_HEIGHT = 10;
    private static final int VERTICAL_CENTER = 4 * LINE_HEIGHT / 2;  // 20
    private static final int MAX_TEXT_WIDTH = 90;

    private static final int SELECTED_COLOR = 0xFFFFFF;   // 白(选中行)
    private static final int SELECTED_DARK = -988212;      // 轮廓色(SignRenderer.BLACK_TEXT_OUTLINE_COLOR)
    private static final int CONTEXT_COLOR = 0x666666;     // 灰(上下文行)
    private static final int FULL_LIGHT = 15728880;        // 满光(发光行)

    private final Font font;

    public GlowSignRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(GlowSignBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof GlowSignBlock signBlock)) return;

        pose.pushPose();
        // translateSign (wall 分支)
        pose.translate(0.5F, 0.75F * RENDER_SCALE, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-signBlock.getYRotationDegrees(state)));
        pose.translate(0.0F, -0.3125F, -0.4375F);  // wall extra

        pose.pushPose();
        float ts = 0.015625F * RENDER_SCALE;
        pose.translate(0.0F, TEXT_OFFSET_Y, TEXT_OFFSET_Z);
        pose.scale(ts, -ts, ts);

        List<String> addresses = be.getAddresses();
        // 客户端 selected 从 NBT 读,可能与新同步的 addresses 长度不匹配,这里 clamp
        int selected = addresses.isEmpty() ? 0 : Mth.clamp(be.getSelected(), 0, addresses.size() - 1);
        boolean empty = addresses.isEmpty();

        for (int line = 0; line < 4; line++) {
            String text;
            boolean isHighlight;
            if (empty) {
                text = (line == 1) ? "shift+右键" : "";
                isHighlight = false;
            } else {
                int idx = selected - 1 + line;  // 窗口 [sel-1, sel, sel+1, sel+2]
                text = (idx >= 0 && idx < addresses.size()) ? addresses.get(idx) : "";
                isHighlight = (line == 1);  // 第 2 行高亮
            }

            List<FormattedCharSequence> split = font.split(Component.literal(text), MAX_TEXT_WIDTH);
            FormattedCharSequence fcs = split.isEmpty() ? FormattedCharSequence.EMPTY : split.get(0);
            float x = -font.width(fcs) / 2.0F;
            float y = line * LINE_HEIGHT - VERTICAL_CENTER;

            if (isHighlight) {
                font.drawInBatch8xOutline(fcs, x, y, SELECTED_COLOR, SELECTED_DARK,
                        pose.last().pose(), buffer, FULL_LIGHT);
            } else {
                font.drawInBatch(fcs, x, y, CONTEXT_COLOR, false,
                        pose.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
            }
        }
        pose.popPose();
        pose.popPose();
    }
}
