package icu.dreamripples.aero_suite.simplification.block;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.content.kinetics.motor.KineticScrollValueBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 变速式便携引擎的转速弹板:离散 15 档(32..256,每档 16)× 2 方向(正/负)。
 * 弹板值 = 档位索引(±1..±15),formatter 翻译成 RPM 显示,使弹板数字与实际转速一致。
 * 两行 ↳/↲ 选方向(同创造马达 KineticScrollValueBehaviour),设置板上限 15 步进 1。
 */
public class SpeedTierScrollBehaviour extends KineticScrollValueBehaviour {

    private static final int[] RPM_TABLE = {
            32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 256
    };

    public SpeedTierScrollBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
        super(label, be, slot);
        between(-15, 15);
        withFormatter(v -> {
            if (v == 0) return "0";
            int idx = Math.min(14, Math.abs(v) - 1);
            return String.valueOf(RPM_TABLE[idx]);
        });
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        ImmutableList<Component> rows = ImmutableList.of(
                Component.literal("\u27f3").withStyle(ChatFormatting.BOLD),
                Component.literal("\u27f2").withStyle(ChatFormatting.BOLD));
        ValueSettingsFormatter formatter = new ValueSettingsFormatter(this::formatSettings);
        // Create 弹板列为 0..maxValue 含端点:传 14 得 0..14 共 15 格,列值+1 = 档位 1..15
        return new ValueSettingsBoard(label, 14, 1, rows, formatter);
    }

    @Override
    public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
        int value = Math.max(1, Math.min(15, valueSetting.value() + 1));
        if (!valueSetting.equals(getValueSettings()))
            playFeedbackSound(this);
        setValue(valueSetting.row() == 0 ? -value : value);
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(value < 0 ? 0 : 1, Math.max(0, Math.abs(value) - 1));
    }

    @Override
    public MutableComponent formatSettings(ValueSettings settings) {
        int v = Math.max(1, Math.min(15, settings.value() + 1));
        return CreateLang.number(RPM_TABLE[v - 1])
                .add(CreateLang.text(settings.row() == 0 ? "\u27f3" : "\u27f2")
                        .style(ChatFormatting.BOLD))
                .component();
    }

    @Override
    public void setValue(int value) {
        // 跳过 0:从正向滚到 0 时切到 -1,从负向滚到 0 时切到 1,使值域为 ±1..±15
        if (value == 0)
            value = (this.value > 0) ? -1 : 1;
        super.setValue(value);
    }

    /** 返回带符号 RPM(正负 = 方向),0 = 停转。 */
    public float getSignedRpm() {
        if (value == 0) return 0;
        int idx = Math.min(14, Math.abs(value) - 1);
        return RPM_TABLE[idx] * Math.signum(value);
    }
}
