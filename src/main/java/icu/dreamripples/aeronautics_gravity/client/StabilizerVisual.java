package icu.dreamripples.aeronautics_gravity.client;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import icu.dreamripples.aeronautics_gravity.block.StabilizerBlock;
import icu.dreamripples.aeronautics_gravity.block.StabilizerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/**
 * 自稳定方块灯带 visual - 读 BlockState.MASS_TIER / LIFT_TIER(互斥),按当前模式染色灯带:
 *   - mass 模式(mass_tier>1):暗红 -> 亮红(向下力语义,同红石配重)
 *   - lift 模式(lift_tier>1):暗青 -> 亮青(向上力语义,同红石配轻)
 *   - 中性(1,1):暗灰(休眠)
 * 档位越高灯带越亮(frac = (tier-1)/15)。无 NBT sync:tier 已编码在 BlockState,自动同步到客户端。
 */
public class StabilizerVisual
        extends AbstractBlockEntityVisual<StabilizerBlockEntity>
        implements SimpleDynamicVisual {

    // mass 模式色带:暗红 -> 亮红
    private static final int MASS_OFF = 0xFF560101;
    private static final int MASS_ON  = 0xFFCD0000;
    // lift 模式色带:暗青 -> 亮青
    private static final int LIFT_OFF = 0xFF013A3A;
    private static final int LIFT_ON  = 0xFF00CDCD;
    // 休眠:暗灰
    private static final int IDLE     = 0xFF222222;

    private final TransformedInstance indicator;
    private int lastHash = -1;

    public StabilizerVisual(VisualizationContext ctx, StabilizerBlockEntity be, float partialTick) {
        super(ctx, be, partialTick);
        this.indicator = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(ModPartialModels.REDSTONE_INDICATOR))
                .createInstance();
        this.indicator.setIdentityTransform()
                .translate(this.getVisualPosition());
        applyColor();
    }

    @Override
    public void beginFrame(Context context) {
        int hash = computeHash();
        if (hash != lastHash) {
            lastHash = hash;
            applyColor();
        }
    }

    private int computeHash() {
        BlockState state = blockEntity.getBlockState();
        return state.getValue(StabilizerBlock.MASS_TIER) * 31 + state.getValue(StabilizerBlock.LIFT_TIER);
    }

    private void applyColor() {
        BlockState state = blockEntity.getBlockState();
        int massTier = state.getValue(StabilizerBlock.MASS_TIER);
        int liftTier = state.getValue(StabilizerBlock.LIFT_TIER);
        int color;
        if (massTier > 1) {
            float frac = (massTier - 1) / 15F;
            color = mixArgb(MASS_OFF, MASS_ON, frac);
        } else if (liftTier > 1) {
            float frac = (liftTier - 1) / 15F;
            color = mixArgb(LIFT_OFF, LIFT_ON, frac);
        } else {
            color = IDLE;
        }
        this.indicator.colorArgb(color);
        this.indicator.setChanged();
    }

    private static int mixArgb(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override
    public void updateLight(float v) {
        relight(indicator);
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(indicator);
    }

    @Override
    protected void _delete() {
        indicator.delete();
    }
}
