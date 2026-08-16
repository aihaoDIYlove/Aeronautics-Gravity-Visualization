package icu.dreamripples.aero_suite.block;

import icu.dreamripples.aero_suite.fluid.ModFluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * 虚空软管滑轮的 {@link IFluidHandler}: 末地之海区域(软管末端 y &lt;= startY)时 {@code drain}
 * 返回无限星空液体, {@code fill} 返回 0(只抽不注)。
 * <p>
 * 绕过 Create 的 {@code FluidDrainingBehaviour}(虚空无 FluidState 必空转)。active 判定委托
 * {@link VoidHosePulleyBlockEntity#isEndSeaActive()}(与护目镜共用, 避免重复)。
 */
public class VoidHosePulleyFluidHandler implements IFluidHandler {

    private final VoidHosePulleyBlockEntity be;

    public VoidHosePulleyFluidHandler(VoidHosePulleyBlockEntity be) {
        this.be = be;
    }

    private boolean isActive() {
        return be.isEndSeaActive();
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return isActive() ? new FluidStack(ModFluids.STARLIGHT.get(), 1000) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return 1000;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (!isActive() || resource.isEmpty()) return FluidStack.EMPTY;
        if (!resource.is(ModFluids.STARLIGHT.get())) return FluidStack.EMPTY;
        return new FluidStack(ModFluids.STARLIGHT.get(), resource.getAmount());
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (!isActive() || maxDrain <= 0) return FluidStack.EMPTY;
        return new FluidStack(ModFluids.STARLIGHT.get(), maxDrain);
    }
}
