package info.nightscout.androidaps.skins

import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinLowRes @Inject constructor(private val config: Config): SkinInterface {

    override val description: Int get() = R.string.lowres_description
    override val mainGraphHeight: Int get() = 200
    override val secondaryGraphHeight: Int get() = 150

    override fun overviewLayout(isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean): Int =
        when {
            config.NSCLIENT && isTablet  -> R.layout.overview_fragment_nsclient_tablet
            config.NSCLIENT              -> R.layout.overview_fragment_nsclient
            isLandscape                  -> R.layout.overview_fragment_landscape
            else                         -> R.layout.overview_fragment
        }
    override fun actionsLayout(isLandscape : Boolean, isSmallWidth : Boolean): Int =
        when {
            isLandscape || !isSmallWidth -> R.layout.actions_fragment
            else                         -> R.layout.actions_fragment_lowres
        }
}