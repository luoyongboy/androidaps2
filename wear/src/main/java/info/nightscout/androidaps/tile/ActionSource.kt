package info.nightscout.androidaps.tile

import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.TileConfigurationActivity
import info.nightscout.androidaps.interaction.actions.BolusActivity
import info.nightscout.androidaps.interaction.actions.ECarbActivity
import info.nightscout.androidaps.interaction.actions.TempTargetActivity

object ActionSource: TileSource {

    override fun getActions(): List<Action> {
        return listOf(
            Action(
                id = 0,
                settingName = "wizzard",
                nameRes = R.string.menu_wizard,
                iconRes = R.drawable.ic_calculator_green,
                activityClass = TileConfigurationActivity::class.java.getName(),
                background = false,
                actionString = "",
            ),
            Action(
                id = 1,
                settingName = "bolus",
                nameRes = R.string.action_bolus,
                iconRes = R.drawable.ic_bolus_carbs,
                activityClass = BolusActivity::class.java.getName(),
                background = false,
                actionString = "",
            ),
            Action(
                id = 2,
                settingName = "carbs",
                nameRes = R.string.action_carbs,
                iconRes = R.drawable.ic_carbs_orange,
                activityClass = ECarbActivity::class.java.getName(),
                background = false,
                actionString = "",
            ),
            Action(
                id = 3,
                settingName = "temp_target",
                nameRes = R.string.menu_tempt,
                iconRes = R.drawable.ic_temptarget_flat,
                activityClass = TempTargetActivity::class.java.getName(),
                background = false,
                actionString = "",
            )
        )
    }
}
