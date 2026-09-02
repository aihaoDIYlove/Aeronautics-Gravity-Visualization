package icu.dreamripples.aero_suite.simplification.block;

import java.util.List;
import java.util.function.Function;

import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.BlockHelper;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 滚动加工台:伪装式滚动的单物品台面(参考 Create ItemDrain 的 TransportedItemStack
 * 滚动 + Depot 的 BeltProcessingBehaviour 加工对接,均为 MIT)。
 * <p>
 * 物品从任意水平侧面进入(beltPosition=0)沿进入轴以 1/8 格/tk 滚动;从上方进入则
 * 居中(0.5)。滚动跨过台面中心且正上方(above(1)/above(2))有注册了
 * {@link BeltProcessingBehaviour} 的加工器(部署器/动力锯等)且物品可被其加工
 * (回调返回 HOLD)时,物品停在中心冻结直至加工完毕(PASS 后继续滚动);不可加工
 * 则照常滚过。终点:先被台面上的黄铜/安山漏斗吸走(tryExportingToBeltFunnel),
 * 再尝试相邻 DirectBeltInputBehaviour,都没有则弹出真实掉落物(ItemDrain 语义)。
 * <p>
 * 产物经 {@link TransportedItemStackHandlerBehaviour}(部署器 activate 调
 * handleProcessingOnItem)写回:第一件留在台面继续滚,其余进 outputBuffer,满则掉落。
 */
public class RollingTableBlockEntity extends SmartBlockEntity {

	public static final int OUTPUT_BUFFER_SLOTS = 8;

	TransportedItemStack heldItem;
	public TransportedItemStack getHeldItem() {
		return heldItem;
	}
	final ItemStackHandler outputBuffer;
	private TransportedItemStackHandlerBehaviour transportedHandler;
	private final RollingTableItemHandler itemHandler;

	public RollingTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		itemHandler = new RollingTableItemHandler(this);
		outputBuffer = new ItemStackHandler(OUTPUT_BUFFER_SLOTS) {
			@Override
			protected void onContentsChanged(int slot) {
				setChanged();
			}
		};
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnels()
			.setInsertionHandler(this::tryInsertingFromSide));
		transportedHandler = new TransportedItemStackHandlerBehaviour(this, this::applyToAllItems);
		behaviours.add(transportedHandler);
	}

	public ItemStack getHeldItemStack() {
		return heldItem == null ? ItemStack.EMPTY : heldItem.stack;
	}

	public void setHeldItem(TransportedItemStack heldItem, Direction insertedFrom) {
		this.heldItem = heldItem;
		this.heldItem.insertedFrom = insertedFrom;
	}

	private ItemStack tryInsertingFromSide(TransportedItemStack transportedStack, Direction side, boolean simulate) {
		if (heldItem != null)
			return transportedStack.stack;
		if (simulate)
			return ItemStack.EMPTY;

		transportedStack = transportedStack.copy();
		transportedStack.beltPosition = side.getAxis().isVertical() ? .5f : 0;
		transportedStack.prevBeltPosition = transportedStack.beltPosition;
		transportedStack.prevSideOffset = transportedStack.sideOffset;
		setHeldItem(transportedStack, side);
		// 上方投入的物品直接停在中心,当场询问加工器(可加工则冻结,否则下个 tick 照常滚出)
		if (side.getAxis().isVertical())
			tryEngageProcessing(true);
		setChanged();
		sendData();
		return ItemStack.EMPTY;
	}

	@Override
	public void tick() {
		super.tick();

		if (heldItem == null) {
			// 批量加工:产物第一件留台面,其余滞留 outputBuffer(无机械臂来清),
			// 台面一空就逐件放回台面滚出去,否则会"凭空消失"
			if (!level.isClientSide)
				drainOutputBuffer();
			return;
		}

		boolean onClient = level.isClientSide && !isVirtual();

		if (heldItem.locked) {
			heldItem.prevBeltPosition = heldItem.beltPosition;
			heldItem.prevSideOffset = heldItem.sideOffset;
			if (onClient)
				return;
			BeltProcessingBehaviour processingBehaviour = findProcessingBehaviour();
			if (processingBehaviour == null || BeltProcessingBehaviour.isBlocked(level, worldPosition)) {
				// 加工器被拆/堵塞:解冻继续滚
				heldItem.locked = false;
				sendData();
				return;
			}
			heldItem.beltPosition = .5f;
			ItemStack before = heldItem.stack;
			ProcessingResult result = processingBehaviour.handleHeldItem(heldItem, transportedHandler);
			if (heldItem == null || result == ProcessingResult.REMOVE) {
				heldItem = null;
				sendData();
				return;
			}
			heldItem.locked = result == ProcessingResult.HOLD;
			if (!heldItem.locked || !ItemStack.matches(before, heldItem.stack))
				sendData();
			return;
		}

		heldItem.prevBeltPosition = heldItem.beltPosition;
		heldItem.prevSideOffset = heldItem.sideOffset;
		heldItem.beltPosition += 1 / 8f;

		if (onClient)
			return;

		// 滚过中心:上方有加工器且愿意接手(HOLD)则停在中心冻结
		if (heldItem.prevBeltPosition < .5f && heldItem.beltPosition >= .5f)
			tryEngageProcessing(false);

		if (heldItem.locked)
			return;

		if (heldItem.beltPosition > 1) {
			heldItem.beltPosition = 1;

			Direction side = heldItem.insertedFrom;

			// 台面上的黄铜/安山漏斗吸走
			ItemStack tryExportingToBeltFunnel = getBehaviour(DirectBeltInputBehaviour.TYPE)
				.tryExportingToBeltFunnel(heldItem.stack, side.getOpposite(), false);
			if (tryExportingToBeltFunnel != null) {
				if (tryExportingToBeltFunnel.getCount() != heldItem.stack.getCount()) {
					if (tryExportingToBeltFunnel.isEmpty())
						heldItem = null;
					else
						heldItem.stack = tryExportingToBeltFunnel;
					notifyUpdate();
					return;
				}
				if (!tryExportingToBeltFunnel.isEmpty())
					return;
			}

			// 相邻运输设施(传送带/漏斗口/另一台加工台)接手
			BlockPos nextPosition = worldPosition.relative(side);
			DirectBeltInputBehaviour directBeltInputBehaviour =
				BlockEntityBehaviour.get(level, nextPosition, DirectBeltInputBehaviour.TYPE);
			if (directBeltInputBehaviour == null) {
				// 无阻挡:弹出真实掉落物(ItemDrain 语义)
				if (!BlockHelper.hasBlockSolidSide(level.getBlockState(nextPosition), level, nextPosition,
					side.getOpposite())) {
					ItemStack ejected = heldItem.stack;
					Vec3 outPos = VecHelper.getCenterOf(worldPosition)
						.add(Vec3.atLowerCornerOf(side.getNormal())
							.scale(.75));
					float movementSpeed = 1 / 8f;
					Vec3 outMotion = Vec3.atLowerCornerOf(side.getNormal())
						.scale(movementSpeed)
						.add(0, 1 / 8f, 0);
					outPos.add(outMotion.normalize());
					ItemEntity entity = new ItemEntity(level, outPos.x, outPos.y + 6 / 16f, outPos.z, ejected);
					entity.setDeltaMovement(outMotion);
					entity.setDefaultPickUpDelay();
					entity.hurtMarked = true;
					level.addFreshEntity(entity);

					heldItem = null;
					notifyUpdate();
				}
				return;
			}

			if (!directBeltInputBehaviour.canInsertFromSide(side))
				return;

			ItemStack returned = directBeltInputBehaviour.handleInsertion(heldItem.copy(), side, false);

			if (returned.isEmpty()) {
				heldItem = null;
				notifyUpdate();
				return;
			}

			if (returned.getCount() != heldItem.stack.getCount()) {
				heldItem.stack = returned;
				notifyUpdate();
				return;
			}
		}
	}

	/**
	 * 尝试与上方加工器对接:物品移到中心并询问 {@code handleReceivedItem};
	 * HOLD → 锁定(true),PASS/REMOVE → false(滚过/消失)。
	 *
	 * @param allowUnlockedReturn 上方投入时物品无速度,加工器未就绪(WAITING 前的
	 *                            HOLD)与可加工无法区分,统一按锁定处理
	 */
	private void tryEngageProcessing(boolean centeredInsert) {
		BeltProcessingBehaviour processingBehaviour = findProcessingBehaviour();
		if (processingBehaviour == null || BeltProcessingBehaviour.isBlocked(level, worldPosition))
			return;

		heldItem.beltPosition = .5f;
		heldItem.prevBeltPosition = .5f;
		ItemStack before = heldItem.stack;
		ProcessingResult result = processingBehaviour.handleReceivedItem(heldItem, transportedHandler);
		if (heldItem == null || result == ProcessingResult.REMOVE) {
			heldItem = null;
			return;
		}
		heldItem.locked = result == ProcessingResult.HOLD;
		if (centeredInsert)
			// 上方投入:deployer 未就绪也返回 HOLD,但台面物品不会离开,按锁定处理等它就绪
			heldItem.locked = true;
		if (heldItem.locked || !ItemStack.matches(before, heldItem.stack))
			sendData();
	}

	/** 加工位:贴台面(above(1))优先,兼容原版传送带式留空(above(2))。 */
	private BeltProcessingBehaviour findProcessingBehaviour() {
		BeltProcessingBehaviour behaviour =
			BlockEntityBehaviour.get(level, worldPosition.above(), BeltProcessingBehaviour.TYPE);
		if (behaviour != null)
			return behaviour;
		return BlockEntityBehaviour.get(level, worldPosition.above(2), BeltProcessingBehaviour.TYPE);
	}

	/** 部署器/锯经 transportedHandler 回写产物:第一件留台面,其余进 outputBuffer,溢出掉落。 */
	private void applyToAllItems(float maxDistanceFromCentre,
								 Function<TransportedItemStack, TransportedResult> processFunction) {
		if (heldItem == null)
			return;
		if (.5f - heldItem.beltPosition > maxDistanceFromCentre)
			return;

		ItemStack stackBefore = heldItem.stack.copy();
		TransportedResult result = processFunction.apply(heldItem);
		if (result == null || result.didntChangeFrom(stackBefore))
			return;

		heldItem = null;
		if (result.hasHeldOutput())
			setCenteredHeldItem(result.getHeldOutput());

		for (TransportedItemStack added : result.getOutputs()) {
			if (getHeldItemStack().isEmpty()) {
				setCenteredHeldItem(added);
				continue;
			}
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(outputBuffer, added.stack, false);
			if (!remainder.isEmpty())
				Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5,
					worldPosition.getZ() + .5, remainder);
		}
		// convertToAndLeaveHeld 的 held 输出可能是空栈(输入只剩 1 个时),清掉防卡台面
		if (heldItem != null && heldItem.stack.isEmpty())
			heldItem = null;
		notifyUpdate();
	}

	private void setCenteredHeldItem(TransportedItemStack heldItem) {
		this.heldItem = heldItem;
		this.heldItem.beltPosition = .5f;
		this.heldItem.prevBeltPosition = .5f;
	}

	private void drainOutputBuffer() {
		for (int i = 0; i < outputBuffer.getSlots(); i++) {
			ItemStack out = outputBuffer.getStackInSlot(i);
			if (out.isEmpty())
				continue;
			outputBuffer.setStackInSlot(i, ItemStack.EMPTY);
			TransportedItemStack tis = new TransportedItemStack(out);
			tis.insertedFrom = Direction.NORTH;
			tis.beltPosition = 0;
			tis.prevBeltPosition = 0;
			heldItem = tis;
			notifyUpdate();
			return;
		}
	}

	public RollingTableItemHandler getItemHandler() {
		return itemHandler;
	}

	@Override
	public void invalidate() {
		super.invalidate();
		invalidateCapabilities();
	}

	@Override
	public void destroy() {
		super.destroy();
		if (heldItem != null && !heldItem.stack.isEmpty())
			Block.popResource(level, worldPosition, heldItem.stack);
		for (int i = 0; i < outputBuffer.getSlots(); i++)
			Block.popResource(level, worldPosition, outputBuffer.getStackInSlot(i));
	}

	@Override
	public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		if (heldItem != null)
			compound.put("HeldItem", heldItem.serializeNBT(registries));
		compound.put("OutputBuffer", outputBuffer.serializeNBT(registries));
		super.write(compound, registries, clientPacket);
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		heldItem = null;
		if (compound.contains("HeldItem"))
			heldItem = TransportedItemStack.read(compound.getCompound("HeldItem"), registries);
		outputBuffer.deserializeNBT(registries, compound.getCompound("OutputBuffer"));
		super.read(compound, registries, clientPacket);
	}

}
