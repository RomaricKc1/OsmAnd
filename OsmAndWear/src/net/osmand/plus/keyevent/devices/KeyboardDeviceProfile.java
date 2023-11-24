package net.osmand.plus.keyevent.devices;

import android.content.Context;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import net.osmand.plus.R;
import net.osmand.plus.keyevent.commands.ActivityBackPressedCommand;
import net.osmand.plus.keyevent.commands.BackToLocationCommand;
import net.osmand.plus.keyevent.commands.EmitNavigationHintCommand;
import net.osmand.plus.keyevent.commands.MapScrollCommand;
import net.osmand.plus.keyevent.commands.MapZoomCommand;
import net.osmand.plus.keyevent.commands.OpenNavigationDialogCommand;
import net.osmand.plus.keyevent.commands.OpenQuickSearchDialogCommand;
import net.osmand.plus.keyevent.commands.SwitchAppModeCommand;
import net.osmand.plus.keyevent.commands.SwitchCompassCommand;
import net.osmand.plus.keyevent.commands.ToggleDrawerCommand;
import net.osmand.plus.plugins.PluginsHelper;

public class KeyboardDeviceProfile extends PredefinedInputDeviceProfile {

	public static final String ID = "keyboard";

	/**
	 * Collects default bindings, which are common for device sub-profiles.
	 * Some types of devices may not support some of the keycodes.
	 */
	@Override
	protected void collectCommands() {
		// Default letter keycodes
		bindCommand(KeyEvent.KEYCODE_C, BackToLocationCommand.ID);
		bindCommand(KeyEvent.KEYCODE_D, SwitchCompassCommand.ID);
		bindCommand(KeyEvent.KEYCODE_N, OpenNavigationDialogCommand.ID);
		bindCommand(KeyEvent.KEYCODE_S, OpenQuickSearchDialogCommand.ID);
		bindCommand(KeyEvent.KEYCODE_P, SwitchAppModeCommand.SWITCH_TO_NEXT_ID);
		bindCommand(KeyEvent.KEYCODE_O, SwitchAppModeCommand.SWITCH_TO_PREVIOUS_ID);

		// Default map scroll keycodes
		bindCommand(KeyEvent.KEYCODE_DPAD_UP, MapScrollCommand.SCROLL_UP_ID);
		bindCommand(KeyEvent.KEYCODE_DPAD_DOWN, MapScrollCommand.SCROLL_DOWN_ID);
		bindCommand(KeyEvent.KEYCODE_DPAD_LEFT, MapScrollCommand.SCROLL_LEFT_ID);
		bindCommand(KeyEvent.KEYCODE_DPAD_RIGHT, MapScrollCommand.SCROLL_RIGHT_ID);

		// Default map zoom keycodes
		bindCommand(KeyEvent.KEYCODE_PLUS, MapZoomCommand.ZOOM_IN_ID);
		bindCommand(KeyEvent.KEYCODE_EQUALS, MapZoomCommand.ZOOM_IN_ID);
		bindCommand(KeyEvent.KEYCODE_MINUS, MapZoomCommand.ZOOM_OUT_ID);

		// Other default keycodes
		bindCommand(KeyEvent.KEYCODE_DPAD_CENTER, EmitNavigationHintCommand.ID);
		bindCommand(KeyEvent.KEYCODE_MENU, ToggleDrawerCommand.ID);
		bindCommand(KeyEvent.KEYCODE_BACK, ActivityBackPressedCommand.ID);

		PluginsHelper.bindCommonKeyEventCommands(this);
	}

	@NonNull
	@Override
	public String getId() {
		return ID;
	}

	@NonNull
	@Override
	public String toHumanString(@NonNull Context context) {
		return context.getString(R.string.sett_generic_ext_input);
	}
}
