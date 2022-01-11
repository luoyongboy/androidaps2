package info.nightscout.androidaps.tile

import android.util.Log
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import androidx.core.content.ContextCompat
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DeviceParametersBuilders.DeviceParameters
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.LayoutElementBuilders.Box
import androidx.wear.tiles.LayoutElementBuilders.Column
import androidx.wear.tiles.LayoutElementBuilders.FontStyles
import androidx.wear.tiles.LayoutElementBuilders.Image
import androidx.wear.tiles.LayoutElementBuilders.Layout
import androidx.wear.tiles.LayoutElementBuilders.LayoutElement
import androidx.wear.tiles.LayoutElementBuilders.Row
import androidx.wear.tiles.LayoutElementBuilders.Spacer
import androidx.wear.tiles.LayoutElementBuilders.Text
import androidx.wear.tiles.ModifiersBuilders.Background
import androidx.wear.tiles.ModifiersBuilders.Clickable
import androidx.wear.tiles.ModifiersBuilders.Corner
import androidx.wear.tiles.ModifiersBuilders.Modifiers
import androidx.wear.tiles.ModifiersBuilders.Semantics
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.ResourceBuilders.AndroidImageResourceByResId
import androidx.wear.tiles.ResourceBuilders.ImageResource
import androidx.wear.tiles.ResourceBuilders.Resources
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders.Timeline
import androidx.wear.tiles.TimelineBuilders.TimelineEntry
import com.google.common.util.concurrent.ListenableFuture
import info.nightscout.androidaps.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future

private const val CIRCLE_SIZE = 75f
private val ICON_SIZE = dp(25f)
private val SPACING_ACTIONS = dp(3f)
private const val BUTTON_COLOR = R.color.gray_850
const val TAG = "ASTAG-tile"

interface TileSource {

    fun getActions(): List<Action>
    fun getDefaultConfig(): Map<String, String>
}

data class Action(
    val id: Int,
    val settingName: String,
    @StringRes val nameRes: Int,
    val activityClass: String,
    @DrawableRes val iconRes: Int,
    val background: Boolean?,
    val actionString: String?,
)

abstract open class TileBase : TileService() {

    open val preferencePrefix = "tile_action_"
    open val resourceVersion = "1"
    open val idIconActionPrefix = "ic_action_"
    open val source: TileSource = ActionSource

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<Tile> = serviceScope.future {
        Log.i(TAG, "onTileRequest: ")
        val actionsSelected = getSelectedActions()
        Tile.Builder()
            .setResourcesVersion(resourceVersion)
            .setTimeline(
                Timeline.Builder().addTimelineEntry(
                    TimelineEntry.Builder().setLayout(
                        Layout.Builder().setRoot(layout(actionsSelected, requestParams.deviceParameters!!)).build()
                    ).build()
                ).build()
            )
            .build()
    }

    override fun onResourcesRequest(
        requestParams: ResourcesRequest
    ): ListenableFuture<Resources> = serviceScope.future {
        Log.i(TAG, "onResourcesRequest: ")
        Resources.Builder()
            .setVersion(resourceVersion)
            .apply {
                source.getActions().mapNotNull { action ->
                    addIdToImageMapping(
                        idIconActionPrefix + action.id,
                        ImageResource.Builder()
                            .setAndroidResourceByResId(
                                AndroidImageResourceByResId.Builder()
                                    .setResourceId(action.iconRes)
                                    .build()
                            )
                            .build()
                    )
                }
            }
            .build()
    }

    private fun addRowSingle(action1: Action, deviceParameters: DeviceParameters): LayoutElement =
        Row.Builder()
            .addContent(action(action1, deviceParameters))
            .build()

    private fun addRowDouble(action1: Action, action2: Action, deviceParameters: DeviceParameters): LayoutElement =
        Row.Builder()
            .addContent(action(action1, deviceParameters))
            .addContent(Spacer.Builder().setWidth(SPACING_ACTIONS).build())
            .addContent(action(action2, deviceParameters))
            .build()

    private fun layout(actions: List<Action>, deviceParameters: DeviceParameters): LayoutElement {
        if (actions.isNotEmpty()) {
            val b = Column.Builder()
            if (actions.size == 1 || actions.size == 3) {
                b.addContent(addRowSingle(actions[0], deviceParameters))
            }
            if (actions.size == 4 || actions.size == 2) {
                b.addContent(addRowDouble(actions[0], actions[1], deviceParameters))
            }
            if (actions.size == 3) {
                b.addContent(addRowDouble(actions[1], actions[2], deviceParameters))
            }
            if (actions.size == 4) {
                b.addContent(Spacer.Builder().setHeight(SPACING_ACTIONS).build())
                    .addContent(addRowDouble(actions[2], actions[3], deviceParameters))
            }
            return b.build()
        }
        return Text.Builder()
            .setText(resources.getString(R.string.tile_no_config))
            .build()
    }

    private fun doAction(action: Action): ActionBuilders.Action {
        val ab = ActionBuilders.AndroidActivity.Builder()
            .setClassName(action.activityClass)
            .setPackageName(this.packageName)
        if (action.actionString != null) {
            ab.addKeyToExtraMapping("actionString", ActionBuilders.AndroidStringExtra.Builder().setValue(action.actionString).build())
        }
        if (action.background != null) {
            ab.addKeyToExtraMapping("inBackground", ActionBuilders.AndroidBooleanExtra.Builder().setValue(action.background).build())
        }
        return ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(ab.build())
            .build()
    }

    private fun action(action: Action, deviceParameters: DeviceParameters) = Box.Builder()
        .setWidth(dp(CIRCLE_SIZE))
        .setHeight(dp(CIRCLE_SIZE))
        .setModifiers(
            Modifiers.Builder()
                .setBackground(
                    Background.Builder()
                        .setColor(
                            argb(ContextCompat.getColor(baseContext, BUTTON_COLOR))
                        )
                        .setCorner(
                            Corner.Builder().setRadius(dp(CIRCLE_SIZE / 2)).build()
                        )
                        .build()
                )
                .setSemantics(
                    Semantics.Builder()
                        .setContentDescription(resources.getString(action.nameRes))
                        .build()
                )
                .setClickable(
                    Clickable.Builder()
                        .setOnClick(doAction(action))
                        .build()
                )
                .build()
        )
        .addContent(
            Column.Builder()
                .addContent(
                    Image.Builder()
                        .setWidth(ICON_SIZE)
                        .setHeight(ICON_SIZE)
                        .setResourceId(idIconActionPrefix + action.id)
                        .build()
                ).addContent(
                    Text.Builder()
                        .setText(resources.getString(action.nameRes))
                        .setFontStyle(
                            FontStyles
                                .button(deviceParameters)
                                .setColor(
                                    argb(ContextCompat.getColor(baseContext, R.color.white))
                                )
                                .build()
                        )
                        .build()
                ).build()
        )
        .build()

    private fun getSelectedActions(): List<Action> {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        setDefaultSettings(sharedPrefs)

        val actionList: MutableList<Action> = mutableListOf()
        for (i in 0..4) {
            val action = getActionFromPreference(sharedPrefs, i)
            if (action != null) {
                actionList.add(action)
            }
        }
        Log.i(TAG, this::class.java.name + ".getSelectedActions: " + actionList.size + " " + actionList.toString())
        if (actionList.isEmpty()) {
            Log.i(TAG, "getSelectedActions: default")
            return source.getActions().take(4)
        }
        return actionList
    }

    private fun getActionFromPreference(sharedPrefs: SharedPreferences, index: Int): Action? {
        val actionPref = sharedPrefs?.getString(preferencePrefix + index, "none")
        return source.getActions().find { a -> a.settingName == actionPref }
    }

    fun setDefaultSettings(sharedPrefs: SharedPreferences) {
        val defaults = source.getDefaultConfig()
        val firstKey = defaults.firstNotNullOf { d -> d.key }
        if (!sharedPrefs.contains(firstKey)) {
            Log.i(TAG, "setDefaultSettings: set defaults")
            val editor = sharedPrefs.edit()
            for ((key, value) in defaults) {
                println("$key = $value")
                editor.putString(key, value)
            }
            editor.apply()
        }
    }

}
