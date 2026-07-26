package icu.dreamripples.aeronautics_gravity.block;

import com.simibubi.create.content.decoration.palettes.ConnectedGlassBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

// 超轻玻璃: 同轻质玻璃, 但 32x32 贴图(边框更细) + 质量 0.125 + 易碎(sable:fragile)。
public class UltralightGlassBlock extends ConnectedGlassBlock {
    public UltralightGlassBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
