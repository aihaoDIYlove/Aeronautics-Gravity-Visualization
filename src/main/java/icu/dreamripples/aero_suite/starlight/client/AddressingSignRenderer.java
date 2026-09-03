package icu.dreamripples.aero_suite.starlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import icu.dreamripples.aero_suite.starlight.block.AddressingSignBlock;
import icu.dreamripples.aero_suite.starlight.block.AddressingSignBlockEntity;
import com.mojang.math.Axis;
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
 * 寻址牌 BER:不画木牌(纯透明),只画歌词式 4 行文字。
 *
 * 朝向复刻 {@code SignRenderer.translateSign} 的 wall 分支:translate(0.5, 0.75*scale, 0.5) +
 * mulPose(YP, -yRot) + wall extra translate(0, -0.3125, -0.4375)。文字 scale = 0.015625 * 0.6666667,
 * TEXT_OFFSET = (0, 0.33333334, 0.046666667),lineHeight = 10,4 行居中 j = 20(复刻 SignRenderer 常量)。
 *
 * 第 2 行(displayLine=1) = 选中地址:SignText 染色基色 + 发光({@code drawInBatch8xOutline},满光
 * 15728880,outline = vanilla getDarkColor 0.4×)。其余 3 行 = 上下文 [sel-1, sel+1, sel+2]:同基色的
 * 0.4× 暗色、不发光({@code drawInBatch} + POLYGON_OFFSET 防 z-fighting)。染料染色经原版 SignApplicator
 * 通道改 SignText color 后此处自动跟随。空列表时第 2 行显示 "shift+右键" 灰色提示。只画正面(不 mulPose 180)。
 */
@OnlyIn(Dist.CLIENT)
public class AddressingSignRenderer implements BlockEntityRenderer<AddressingSignBlockEntity> {

    private static final float RENDER_SCALE = 0.6666667F;
    private static final float TEXT_OFFSET_Y = 0.33333334F;
    private static final float TEXT_OFFSET_Z = 0.046666667F;
    private static final int LINE_HEIGHT = 10;
    private static final int VERTICAL_CENTER = 4 * LINE_HEIGHT / 2;  // 20
    private static final int MAX_TEXT_WIDTH = 90;

    private static final int FULL_LIGHT = 15728880;        // 满光(发光行)

    private final Font font;

    // RGB 各通道 ×0.4(vanilla SignRenderer.getDarkColor 的主体逻辑,去掉黑字发光的米色特判)
    private static int dimColor(int argb) {
        return net.minecraft.util.FastColor.ARGB32.color(
                0,
                (int) (net.minecraft.util.FastColor.ARGB32.red(argb) * 0.4),
                (int) (net.minecraft.util.FastColor.ARGB32.green(argb) * 0.4),
                (int) (net.minecraft.util.FastColor.ARGB32.blue(argb) * 0.4));
    }


    public AddressingSignRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(AddressingSignBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof AddressingSignBlock signBlock)) return;

        // 染料染色:4 行统一走 SignText 颜色(染料改 SignText color)。高亮行 = 基色 + 发光(outline 色 =
        // vanilla getDarkColor:0.4×,黑字发光特判米色——该特判只用于描边);其余行 = 纯 0.4× 暗色不发光,
        // 不能用 getDarkColor——其黑字发光特判返回浅米色,黑染料时正文会变白(2026-09 踩坑)。
        // 未染色时基色为白:高亮白字深灰 outline、其余 ≈0x636665 灰,与旧硬编码观感一致。
        int baseColor = be.getText(true).getColor().getTextColor();
        int outlineColor = net.minecraft.client.renderer.blockentity.SignRenderer.getDarkColor(be.getText(true));
        int contextColor = dimColor(baseColor);

        pose.pushPose();
        // translateSign (wall 分支)
        pose.translate(0.5F, 0.75F * RENDER_SCALE, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-signBlock.getYRotationDegrees(state)));
        pose.translate(0.0F, -0.3125F, -0.4375F);  // wall extra

        pose.pushPose();
        float ts = 0.015625F * RENDER_SCALE;
        // 贴了伪装板材质时,材质正面在方块边缘(0.5),文字基准面(0.4375+offset≈0.484)会被盖住,
        // 往外推 0.03 露在材质表面之上
        float zOffset = TEXT_OFFSET_Z + (be.hasCustomMaterial() ? 0.03F : 0.0F);
        pose.translate(0.0F, TEXT_OFFSET_Y, zOffset);
        pose.scale(ts, -ts, ts);

        List<String> addresses = be.getAddresses();
        // 客户端 selected 从 NBT 读,可能与新同步的 addresses 长度不匹配,这里 clamp
        int selected = addresses.isEmpty() ? 0 : Mth.clamp(be.getSelected(), 0, addresses.size() - 1);
        boolean empty = addresses.isEmpty();

        for (int line = 0; line < 4; line++) {
            Component text;
            boolean isHighlight;
            if (empty) {
                text = (line == 1) ? Component.translatable("aero_suite.addressing_sign.hint") : Component.empty();
                isHighlight = false;
            } else {
                int idx = selected - 1 + line;  // 窗口 [sel-1, sel, sel+1, sel+2]
                text = (idx >= 0 && idx < addresses.size()) ? Component.literal(addresses.get(idx)) : Component.empty();
                isHighlight = (line == 1);  // 第 2 行高亮
            }

            List<FormattedCharSequence> split = font.split(text, MAX_TEXT_WIDTH);
            FormattedCharSequence fcs = split.isEmpty() ? FormattedCharSequence.EMPTY : split.get(0);
            float x = -font.width(fcs) / 2.0F;
            float y = line * LINE_HEIGHT - VERTICAL_CENTER;

            if (isHighlight) {
                font.drawInBatch8xOutline(fcs, x, y, baseColor, outlineColor,
                        pose.last().pose(), buffer, FULL_LIGHT);
            } else {
                font.drawInBatch(fcs, x, y, contextColor, false,
                        pose.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
            }
        }
        pose.popPose();
        pose.popPose();
    }
}
