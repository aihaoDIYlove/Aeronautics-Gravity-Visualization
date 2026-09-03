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
 * <b>裁剪</b>到寻址牌本体 AABB(原生纹素密度,不拉伸)。默认(无材质/材质为空气)回退原始空模型,
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
        for (BakedQuad quad : materialModel.getQuads(material, side, rand)) {
            quads.add(BakedQuadHelper.cloneWithCustomGeometry(quad,
                    BakedModelHelper.cropAndMove(quad.getVertices(), quad.getSprite(), bb, Vec3.ZERO)));
        }
        return quads;
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
