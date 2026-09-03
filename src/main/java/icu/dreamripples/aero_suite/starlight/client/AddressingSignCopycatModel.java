package icu.dreamripples.aero_suite.starlight.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.simibubi.create.foundation.model.BakedModelHelper;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import com.simibubi.create.foundation.model.BakedQuadHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * 寻址牌"伪装板"模型:把玩家贴上的材质方块(伪装板 create:copycat_panel 同款机制)的烘焙 quads
 * <b>裁剪</b>到寻址牌本体 AABB(原生纹素密度,不拉伸;几何跨度不足一整张贴图的轴拼接贴图顶部/底部条带、跳过中段,保留方块边缘与四角纹理)。默认(无材质/材质为空气)回退原始空模型,
 * 保持寻址牌纯透明的现状;文字由 {@link AddressingSignRenderer} 照常叠加。
 *
 * 注册:AeronauticsGravityClient.onModifyBaking 经 ModelEvent.ModifyBakingResult 对寻址牌全部
 * blockstate 变体包装(等价本文件其余 CTModel 的做法)。
 *
 * 渲染类型:覆写 {@link #getRenderTypes} 取 base 与材质模型的并集——否则寻址牌 block model 只声明
 * cutout,透明材质(玻璃等)的 quads 会被 chunk 编译跳过。
 */
public class AddressingSignCopycatModel extends BakedModelWrapperWithData {

    public static final ModelProperty<BlockState> MATERIAL_PROPERTY = new ModelProperty<>();

    public AddressingSignCopycatModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, net.minecraft.world.level.BlockAndTintGetter world,
                                                BlockPos pos, BlockState state, ModelData blockEntityData) {
        BlockState material = getMaterial(blockEntityData);
        if (material != null)
            builder.with(MATERIAL_PROPERTY, material);
        return builder;
    }


    private static BlockState getMaterial(ModelData data) {
        BlockState material = data == null ? null : data.get(MATERIAL_PROPERTY);
        return material == null || material.isAir() ? null : material;
    }


    @Override
    @Deprecated // NeoForge 推荐带 ModelData 的重载;此基础重载仍被物品渲染等路径调用,须覆写兜底
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
        return Collections.emptyList();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
                                    RenderType renderType) {
        if (side == null)
            return Collections.emptyList();

        BlockState material = getMaterial(data);
        if (material == null)
            return super.getQuads(state, side, rand, data, renderType);


        BakedModel materialModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
        if (renderType != null && !materialModel.getRenderTypes(material, rand, ModelData.EMPTY).contains(renderType))
            return super.getQuads(state, side, rand, data, renderType);

        AABB bb = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).bounds();
        List<BakedQuad> quads = new ArrayList<>();
        for (BakedQuad quad : materialModel.getQuads(material, side, rand))
            quads.addAll(cropKeepingEdges(quad, bb, side));
        return quads;
    }

    // 方块单位(0~1)——AABB.bounds() 与材质模型顶点都在这套单位里,一整张贴图跨 1.0
    private static final float TEXTURE_SPAN = 1.0F;

    /**
     * 保留边缘的裁剪。普通裁剪(cropAndMove 到 AABB)在牌面只露出贴图中段一条,带边框的方块
     * (原木、砖块等)视觉很怪。改为按轴处理:某轴几何跨度不足一张贴图(16 纹素)时,不取该轴
     * 中段纹理,而是取贴图顶部 span/2 与底部 span/2 两条原生密度条带,分两个 quad 拼进牌面——
     * 中段跳过,四个角与四条边的纹理完整保留;跨度足够的轴仍普通裁剪。
     *
     * 实现依赖 cropAndMove 的两个特性:裁剪时 UV 按原生纹素密度跟随几何收缩(所以先裁到源条带
     * 得到正确 UV);move 只平移几何不动 UV(所以再把条带搬进目标 AABB 完成拼接)。
     * 面法线所在轴不拆分——平面 quad 沿法线裁剪不影响 UV,拆分只会生成重复面。
     */
    private static List<BakedQuad> cropKeepingEdges(BakedQuad quad, AABB bb, Direction side) {
        Direction.Axis normalAxis = side.getAxis();
        @SuppressWarnings("unchecked")
        List<double[]>[] segmentsPerAxis = new List[3];
        for (Direction.Axis axis : Direction.Axis.values()) {
            double gMin = bb.min(axis), gMax = bb.max(axis);
            List<double[]> segs = new ArrayList<>(2);
            if (axis == normalAxis || gMax - gMin >= TEXTURE_SPAN) {
                segs.add(new double[] {gMin, gMax, 0});
            } else {
                double half = (gMax - gMin) / 2;
                // {源起点, 源终点, move};move = 目标起点 - 源起点
                segs.add(new double[] {0, half, gMin});
                segs.add(new double[] {TEXTURE_SPAN - half, TEXTURE_SPAN, gMax - TEXTURE_SPAN});
            }
            segmentsPerAxis[axis.ordinal()] = segs;
        }

        List<BakedQuad> out = new ArrayList<>(4);
        for (double[] sx : segmentsPerAxis[0])
            for (double[] sy : segmentsPerAxis[1])
                for (double[] sz : segmentsPerAxis[2]) {
                    AABB src = new AABB(sx[0], sy[0], sz[0], sx[1], sy[1], sz[1]);
                    Vec3 move = new Vec3(sx[2], sy[2], sz[2]);
                    out.add(BakedQuadHelper.cloneWithCustomGeometry(quad,
                            BakedModelHelper.cropAndMove(quad.getVertices(), quad.getSprite(), src, move)));
                }
        return out;
    }

    @Override
    public net.neoforged.neoforge.client.ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand,
                                                                           ModelData data) {
        net.neoforged.neoforge.client.ChunkRenderTypeSet base = super.getRenderTypes(state, rand, data);
        BlockState material = getMaterial(data);
        if (material == null)
            return base;
        net.neoforged.neoforge.client.ChunkRenderTypeSet materialTypes =
                Minecraft.getInstance().getBlockRenderer().getBlockModel(material).getRenderTypes(material, rand, ModelData.EMPTY);
        if (materialTypes.isEmpty())
            return base;
        return net.neoforged.neoforge.client.ChunkRenderTypeSet.union(base, materialTypes);
    }

    @Override
    public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState material = getMaterial(data);
        if (material == null)
            return super.getParticleIcon(data);
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(material).getParticleIcon(ModelData.EMPTY);
    }
}
