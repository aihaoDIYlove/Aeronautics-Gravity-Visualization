package icu.dreamripples.aero_suite.gravity.client;

import dev.engine_room.flywheel.api.instance.Instance;
import icu.dreamripples.aero_suite.gravity.block.RedstoneCounterweightBlock;
import icu.dreamripples.aero_suite.gravity.block.RedstoneCounterweightBlockEntity;
import icu.dreamripples.aero_suite.starlight.client.ModPartialModels;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;

import java.util.function.Consumer;

/**
 * 红石配重块灯带 visual - 读 BlockState.MASS_TIER(1..16)算红石信号(0..15),
 * 染色灯带从暗红渐变到亮红(同 Simulated SimColors.REDSTONE_OFF/ON)。
 * 无 NBT sync:tier 已编码在 BlockState,自动同步到客户端。
 * 参考 Simulated ThrottleLeverVisual 的 colorArgb 染色机制。
 */
public class RedstoneCounterweightVisual
        extends AbstractBlockEntityVisual<RedstoneCounterweightBlockEntity>
        implements SimpleDynamicVisual {

    // 红石色带:暗红 -> 亮红
    private static final int OFF = 0xFF560101;
    private static final int ON  = 0xFFCD0000;

    private final TransformedInstance indicator;
    private int lastSignal = -1;

    public RedstoneCounterweightVisual(VisualizationContext ctx, RedstoneCounterweightBlockEntity be, float partialTick) {
        super(ctx, be, partialTick);
        this.indicator = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(ModPartialModels.REDSTONE_INDICATOR))
                .createInstance();
        this.indicator.setIdentityTransform()
                .translate(this.getVisualPosition());
        int sig = signalFromTier();
        this.indicator.colorArgb(colorForSignal(sig));
        this.indicator.setChanged();
        this.lastSignal = sig;
    }

    @Override
    public void beginFrame(Context context) {
        int sig = signalFromTier();
        if (sig != lastSignal) {
            lastSignal = sig;
            this.indicator.colorArgb(colorForSignal(sig));
            this.indicator.setChanged();
        }
    }

    private int signalFromTier() {
        // tier 1..16 -> signal 0..15
        return Math.max(0, blockEntity.getBlockState().getValue(RedstoneCounterweightBlock.MASS_TIER) - 1);
    }

    private static int colorForSignal(int signal) {
        float frac = Math.max(0, Math.min(1, signal / 15F));
        return icu.dreamripples.aero_suite.common.client.AeroSuiteColors.mixArgb(OFF, ON, frac);
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
