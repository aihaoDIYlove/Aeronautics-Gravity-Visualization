package icu.dreamripples.aero_suite.block;

import com.simibubi.create.content.decoration.palettes.ConnectedGlassBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

// 继承 Create 的 ConnectedGlassBlock 复用 skipRendering: 相邻同类玻璃不画内面,视觉上连成一片。
// 不需要 CTModel 包装(没有 sprite sheet),贴图是单帧 16x16。
public class LightweightGlassBlock extends ConnectedGlassBlock {
    public LightweightGlassBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
