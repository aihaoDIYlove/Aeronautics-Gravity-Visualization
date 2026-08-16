package icu.dreamripples.aero_suite.simplification.client;

import com.simibubi.create.AllPartialModels;
import icu.dreamripples.aero_suite.simplification.block.ConvenientAnalogTransmissionBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

/**
 * 更方便的模拟传动器的 visual：传动杆两端独立渲染（像齿轮箱），解耦"显示转速"与"网络转速"。
 * <p>
 * 主BE.speed 由 Create kinetic 网络持有，恒为输出转速 targetSpeed（齿轮端输入）或 0（信号0）。
 * 传动杆沿 AXIS 两端：一端是输入（接 source），另一端是输出。两端转速不同：
 * <ul>
 *   <li>输入端 -> 显示 source 邻居的转速（输入转速）</li>
 *   <li>输出端 -> 显示主BE.speed（=targetSpeed 输出，或 0）</li>
 * </ul>
 * 齿轮端输入时两端都是输出（都 targetSpeed）；信号0时输入端仍跟随 source 转动（离合器断开，
 * 输出端不转）。
 * <p>
 * 输入端判定（findInputDirection）：
 * <ol>
 *   <li>主BE.hasSource() 且 source≠主BE pos（传动杆端输入）-> source 方向就是输入端</li>
 *   <li>否则（信号0 source=null，或齿轮端输入 source=extraWheel 同坐标）-> 检查轴两端邻居，
 *       只有一端在转的就是输入端（信号0时输出端负载不转，可靠区分）</li>
 * </ol>
 * 信号0时主BE.source=null（propagateNewSource 在 newSpeed=0 时跳过 setSource），所以不能用 source
 * 判定，必须检查邻居转速。
 * <p>
 * 齿轮 visual（cog）保持读 extraWheel.getSpeed()（齿轮端输入=输入转速，传动杆端输入=targetSpeed）。
 * <p>
 * tick() 兜底轮询两端邻居转速（source 变速在信号0时不触发主BE sendData），变化才重设 shaft。
 */
public class ConvenientAnalogTransmissionVisual extends KineticBlockEntityVisual<ConvenientAnalogTransmissionBlockEntity>
        implements SimpleTickableVisual {

    private final Direction.Axis axis;
    private final Direction posDir;
    private final Direction negDir;

    private final RotatingInstance shaftPositive;
    private final RotatingInstance shaftNegative;
    private final RotatingInstance cogInstance;

    private float lastPosSpeed = Float.NaN;
    private float lastNegSpeed = Float.NaN;

    public ConvenientAnalogTransmissionVisual(VisualizationContext context,
                                             ConvenientAnalogTransmissionBlockEntity blockEntity,
                                             float partialTick) {
        super(context, blockEntity, partialTick);
        this.axis = rotationAxis();
        this.posDir = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        this.negDir = posDir.getOpposite();

        // 两端独立的半轴（SHAFT_HALF 默认沿 Z/SOUTH，rotateToFace 到目标端）
        var shaftInstancer = instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF));
        this.shaftPositive = shaftInstancer.createInstance()
                .setup(blockEntity, axis, speedForFace(posDir))
                .setPosition(getVisualPosition())
                .rotateToFace(Direction.SOUTH, posDir);
        this.shaftPositive.setChanged();
        this.shaftNegative = shaftInstancer.createInstance()
                .setup(blockEntity, axis, speedForFace(negDir))
                .setPosition(getVisualPosition())
                .rotateToFace(Direction.SOUTH, negDir);
        this.shaftNegative.setChanged();

        // 齿轮环（extraWheel），与原 AnalogTransmissionVisual 一致
        this.cogInstance = instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(SimPartialModels.ANALOG_TRANSMISSION_COG)).createInstance()
                .rotateToFace(Direction.UP, axis)
                .setup(blockEntity.getExtraKinetics())
                .setPosition(getVisualPosition());
        this.cogInstance.setChanged();

        this.lastPosSpeed = speedForFace(posDir);
        this.lastNegSpeed = speedForFace(negDir);
    }

    @Override
    public void update(float pt) {
        // 主BE sendData 触发：重算两端 shaft + 更新 cog
        float posSpeed = speedForFace(posDir);
        float negSpeed = speedForFace(negDir);
        this.shaftPositive.setup(blockEntity, axis, posSpeed).setChanged();
        this.shaftNegative.setup(blockEntity, axis, negSpeed).setChanged();
        this.cogInstance.setup(blockEntity.getExtraKinetics()).setChanged();
        this.lastPosSpeed = posSpeed;
        this.lastNegSpeed = negSpeed;
    }

    @Override
    public void tick(TickableVisual.Context context) {
        // 兜底轮询：source 变速在信号0时不触发主BE sendData，这里捕获
        float posSpeed = speedForFace(posDir);
        if (posSpeed != this.lastPosSpeed) {
            this.shaftPositive.setup(blockEntity, axis, posSpeed).setChanged();
            this.lastPosSpeed = posSpeed;
        }
        float negSpeed = speedForFace(negDir);
        if (negSpeed != this.lastNegSpeed) {
            this.shaftNegative.setup(blockEntity, axis, negSpeed).setChanged();
            this.lastNegSpeed = negSpeed;
        }
        // cog 不 tick：extraWheel.speed 经主BE NBT 同步，update() 时已刷新
    }

    @Override
    public void updateLight(float partialTick) {
        relight(this.shaftPositive, this.shaftNegative, this.cogInstance);
    }

    @Override
    protected void _delete() {
        this.shaftPositive.delete();
        this.shaftNegative.delete();
        this.cogInstance.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(this.shaftPositive);
        consumer.accept(this.shaftNegative);
        consumer.accept(this.cogInstance);
    }

    /** 计算指定端的显示转速：输入端=邻居转速，输出端=主BE.speed（targetSpeed）。 */
    private float speedForFace(Direction face) {
        Direction inputDir = findInputDirection();
        if (inputDir != null) {
            if (face == inputDir) {
                return getNeighborSpeed(inputDir);
            }
            return blockEntity.getSpeed();
        }
        // 齿轮端输入或两端都转/都不转：两端都按输出（targetSpeed）
        return blockEntity.getSpeed();
    }

    /**
     * 判定输入端方向。
     * 有 source 且 source 在轴上 -> source 方向。
     * 否则（信号0 source=null / 齿轮端输入）-> 检查两端邻居，只有一端在转的就是输入端。
     * 返回 null 表示无法判定（齿轮端输入或两端状态一致），两端都按输出处理。
     */
    private Direction findInputDirection() {
        BlockPos pos = blockEntity.getBlockPos();
        if (blockEntity.hasSource() && !blockEntity.source.equals(pos)) {
            BlockPos diff = blockEntity.source.subtract(pos);
            Direction d = Direction.getNearest(diff.getX(), diff.getY(), diff.getZ());
            if (d.getAxis() == axis) return d;
        }
        boolean posActive = isNeighborActive(posDir);
        boolean negActive = isNeighborActive(negDir);
        if (posActive && !negActive) return posDir;
        if (negActive && !posActive) return negDir;
        return null;
    }

    private boolean isNeighborActive(Direction dir) {
        return getNeighborSpeed(dir) != 0f;
    }

    private float getNeighborSpeed(Direction dir) {
        BlockEntity be = blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos().relative(dir));
        if (be instanceof KineticBlockEntity kbe) {
            return kbe.getSpeed();
        }
        return 0f;
    }
}
