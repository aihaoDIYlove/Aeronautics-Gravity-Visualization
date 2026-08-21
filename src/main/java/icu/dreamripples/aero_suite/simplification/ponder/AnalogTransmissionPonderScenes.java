package icu.dreamripples.aero_suite.simplification.ponder;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

// 更方便的模拟传动器 - 思索场景(四幕)

public class AnalogTransmissionPonderScenes {

	private static final int SPEED_AT_7 = 128; // 7 档 = 16 * (7+1)

	private static void showAll(CreateSceneBuilder scene, SceneBuildingUtil util) {
		scene.showBasePlate();
		scene.idle(5);
		scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
		scene.idle(10);
	}

	// AnalogTransmission 是 ExtraKinetics 双网络:主 BE(传动杆面)走 Speed 键,
	// 齿轮面(extraWheel)走 ExtraCogwheel.Speed 子标签。setKineticSpeed 只改主 BE,
	// 要让齿轮面与输入齿轮啮合反向,必须显式写 ExtraCogwheel.Speed。
	private static void setExtraCogSpeed(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, float speed) {
		scene.world().modifyBlockEntityNBT(util.select().position(pos), KineticBlockEntity.class,
			nbt -> {
				CompoundTag extra = nbt.getCompound("ExtraCogwheel");
				extra.putFloat("Speed", speed);
				nbt.put("ExtraCogwheel", extra);
			});
	}

	// ------------------------------------------------------------------
	// 幕1: 无红石 = 离合器。侧面齿轮面输入,传动杆面/转速表读 0
	// ------------------------------------------------------------------
	public static void unpoweredClutch(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("convenient_analog_transmission_clutch", "无红石信号时的更方便的模拟传动器");
		scene.configureBasePlate(0, 0, 5);

		BlockPos t = util.grid().at(2, 1, 2);
		BlockPos motorPos = util.grid().at(3, 1, 3);
		BlockPos inputCog = util.grid().at(3, 1, 2);
		// BlockPos outputShaft = util.grid().at(2, 1, 1);
		BlockPos outputGearbox = util.grid().at(2, 1, 0);
		BlockPos westCog = util.grid().at(1, 1, 2);
		BlockPos inputGauge = util.grid().at(1, 1, 3);   // 挨着西侧输入齿轮的转速表
		BlockPos outputGauge = util.grid().at(3, 1, 0);  // 挨着西北输出齿轮箱的转速表

		showAll(scene, util);

		// 先清空 schematic 残留的主 BE 转速(setKineticSpeed 不触碰 ExtraCogwheel 子标签),再明确分配
		scene.world().setKineticSpeed(util.select().everywhere(), 0);
		// 输入侧:马达+东侧小齿轮=-32(与 schematic 的 motor facing=north 一致);
		// 齿轮面(extraWheel)啮合反向=+32;西侧小齿轮再啮合反向=-32;输入转速表跟随西侧齿轮=-32
		scene.world().setKineticSpeed(util.select().position(motorPos).add(util.select().position(inputCog)), -32);
		setExtraCogSpeed(scene, util, t, 32);
		scene.world().setKineticSpeed(util.select().position(westCog), -32);
		scene.world().setKineticSpeed(util.select().position(inputGauge), -32);
		// 输出侧(传动器主 BE/传动杆/齿轮箱)因无红石=0
		scene.world().setKineticSpeed(util.select().fromTo(t, outputGearbox), 0);
		scene.world().setKineticSpeed(util.select().position(outputGauge), 0);
		scene.effects().rotationDirectionIndicator(inputCog);
		scene.idle(10);

		scene.overlay().showText(90)
			.text("无红石信号时,该传动器等效于离合器,不输出任何转速")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().topOf(t));
		scene.idle(40);
		scene.effects().rotationSpeedIndicator(outputGauge);
		scene.idle(55);
		scene.overlay().showText(90)
			.text("仅从侧面齿轮面输入时,侧面齿轮面可像正常齿轮一样传播应力")
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(t, Direction.SOUTH));
		scene.idle(80);
		scene.markAsFinished();
	}

	// ------------------------------------------------------------------
	// 幕2: 同幕1 + 模拟拉杆拨到 7 档 -> 传动杆面输出 128 RPM
	// ------------------------------------------------------------------
	public static void redstoneLookup(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("convenient_analog_transmission_lookup", "用红石信号查表控制输出转速");
		scene.configureBasePlate(0, 0, 5);

		BlockPos t = util.grid().at(2, 1, 2);
		BlockPos motorPos = util.grid().at(3, 1, 3);
		BlockPos inputCog = util.grid().at(3, 1, 2);
		// BlockPos outputShaft = util.grid().at(2, 1, 1);
		BlockPos outputGearbox = util.grid().at(2, 1, 0);
		BlockPos westCog = util.grid().at(1, 1, 2);
		BlockPos inputGauge = util.grid().at(1, 1, 3);   // 挨着西侧输入齿轮的转速表
		BlockPos outputGauge = util.grid().at(3, 1, 0);  // 挨着西北输出齿轮箱的转速表
		BlockPos lever = util.grid().at(2, 2, 2);

		showAll(scene, util);

		// 先保持无红石:输入侧 -32(齿轮面 +32 反向啮合),输出侧 0
		scene.world().setKineticSpeed(util.select().everywhere(), 0);
		scene.world().setKineticSpeed(util.select().position(motorPos).add(util.select().position(inputCog)), -32);
		setExtraCogSpeed(scene, util, t, 32);
		scene.world().setKineticSpeed(util.select().position(westCog), -32);
		scene.world().setKineticSpeed(util.select().position(inputGauge), -32);
		scene.world().setKineticSpeed(util.select().fromTo(t, outputGearbox), 0);
		scene.world().setKineticSpeed(util.select().position(outputGauge), 0);
		scene.idle(20);

		// 右键气泡 -> 拨到 7 档
		scene.overlay().showControls(util.vector().topOf(lever), Pointing.DOWN, 40).rightClick();
		scene.idle(7);
		scene.world().modifyBlockEntityNBT(util.select().position(lever), AnalogLeverBlockEntity.class,
			nbt -> nbt.putInt("State", 7));
		scene.world().toggleRedstonePower(util.select().position(t));
		scene.effects().indicateRedstone(lever);
		scene.idle(15);

		// 输出轴/西北 gearbox/输出转速表 -> 128;输入侧保持 -32/齿轮面保持 +32(啮合反向)/西侧齿轮 -32/输入转速表 -32(与第一幕一致)
		scene.world().setKineticSpeed(util.select().fromTo(t, outputGearbox), SPEED_AT_7);
		scene.world().setKineticSpeed(util.select().position(outputGauge), SPEED_AT_7);
		scene.world().setKineticSpeed(util.select().position(motorPos).add(util.select().position(inputCog)), -32);
		setExtraCogSpeed(scene, util, t, 32);
		scene.world().setKineticSpeed(util.select().position(westCog), -32);
		scene.world().setKineticSpeed(util.select().position(inputGauge), -32);
		scene.effects().rotationSpeedIndicator(outputGauge);
		scene.effects().indicateSuccess(outputGauge);
		scene.idle(10);

		scene.overlay().showText(90)
			.text("该传动器的输出应力根据红石信号查表获得,无论从任何面输入,最终输出都由红石信号强度决定")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().topOf(t));
		scene.idle(90);
		scene.overlay().showText(90)
			.text("红石信号 1 档对应 32 转速,此后每 +1 档红石信号对应 +16 转速,15 档时到达 256 转速")
			.colored(PonderPalette.FAST)
			.placeNearTarget()
			.pointAt(util.vector().topOf(t));
		scene.idle(110); // 多等1秒(20 tick)
		scene.overlay().showText(90)
			.text("由齿轮面输入时,两个传动杆面为输出面")
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().topOf(t));
		scene.idle(80);
		scene.markAsFinished();
	}

	// ------------------------------------------------------------------
	// 幕3: 传动杆面输入 -> 其余 5 面均为输出面
	// ------------------------------------------------------------------
	public static void shaftInput(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("convenient_analog_transmission_shaft_in", "从传动杆面输入");
		scene.configureBasePlate(0, 0, 5);

		BlockPos motorPos = util.grid().at(3, 1, 2);
		BlockPos t = util.grid().at(2, 1, 2);

		showAll(scene, util);

		scene.world().setKineticSpeed(util.select().fromTo(motorPos, t), 32);
		scene.idle(10);

		scene.overlay().showText(90)
			.text("当由传动杆面输入时,其余 5 面均为输出面")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().topOf(t));
		scene.idle(40);

		// 5 个输出面逐个指示: 上/下/南/北/西(对面传动杆面)
		Direction[] outputs = { Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.NORTH, Direction.WEST };
		for (Direction d : outputs) {
			scene.effects().rotationSpeedIndicator(t.relative(d));
			scene.idle(8);
		}
		scene.idle(60);
		scene.markAsFinished();
	}

	// ------------------------------------------------------------------
	// 幕4: 同幕3 + 拉杆 7 档 -> 对面传动杆面输出,转速表读 128
	// ------------------------------------------------------------------
	public static void shaftInputThrough(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("convenient_analog_transmission_pass_through", "传动杆面直通输出");
		scene.configureBasePlate(0, 0, 5);

		BlockPos motorPos = util.grid().at(3, 1, 2);
		BlockPos t = util.grid().at(2, 1, 2);
		BlockPos outputShaft = util.grid().at(1, 1, 2);
		BlockPos gaugePos = util.grid().at(1, 1, 2); // 转速表与输出传动杆同格
		BlockPos lever = util.grid().at(2, 2, 2);

		showAll(scene, util);

		scene.world().setKineticSpeed(util.select().position(motorPos), 32);
		scene.idle(20);

		scene.overlay().showControls(util.vector().topOf(lever), Pointing.DOWN, 40).rightClick();
		scene.idle(7);
		scene.world().modifyBlockEntityNBT(util.select().position(lever), AnalogLeverBlockEntity.class,
			nbt -> nbt.putInt("State", 7));
		scene.effects().indicateRedstone(lever);
		scene.idle(15);

		scene.world().setKineticSpeed(util.select().fromTo(t, outputShaft), SPEED_AT_7);
		scene.world().setKineticSpeed(util.select().position(gaugePos), SPEED_AT_7);
		scene.effects().rotationSpeedIndicator(gaugePos);
		scene.effects().indicateSuccess(gaugePos);
		scene.idle(10);

		scene.overlay().showText(100)
			.text("传动杆面输入时,其对面的传动杆面也可输出。这极其方便")
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(gaugePos, Direction.WEST));
		scene.idle(100);
		scene.markAsFinished();
	}
}
