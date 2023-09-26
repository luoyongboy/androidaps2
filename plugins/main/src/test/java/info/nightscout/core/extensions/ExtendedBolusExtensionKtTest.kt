package info.nightscout.core.extensions

import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.insulin.InsulinLyumjevPlugin
import info.nightscout.interfaces.aps.AutosensResult
import info.nightscout.interfaces.aps.SMBDefaults
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.shared.utils.T
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

class ExtendedBolusExtensionKtTest : TestBaseWithProfile() {

    @Mock lateinit var profileFunctions: ProfileFunction
    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var insulin: Insulin

    private val dia = 7.0

    @BeforeEach fun setup() {
        insulin = InsulinLyumjevPlugin(profileInjector, rh, profileFunctions, rxBus, aapsLogger, config, hardLimits, uiInteraction)
        Mockito.`when`(activePlugin.activeInsulin).thenReturn(insulin)
        Mockito.`when`(dateUtil.now()).thenReturn(now)
    }

    @Test fun iobCalc() {
        val bolus = ExtendedBolus(timestamp = now - 1, amount = 1.0, duration = T.hours(1).msecs())
        // there should zero IOB after now
        assertThat(bolus.iobCalc(now, validProfile, insulin).iob).isWithin(0.01).of(0.0)
        // there should be significant IOB at EB finish
        assertThat(bolus.iobCalc(now + T.hours(1).msecs(), validProfile, insulin).iob).isGreaterThan(0.8)
        // there should be less that 5% after DIA -1
        assertThat(bolus.iobCalc(now + T.hours(dia.toLong() - 1).msecs(), validProfile, insulin).iob).isLessThan(0.05)
        // there should be zero after DIA
        assertThat(bolus.iobCalc(now + T.hours(dia.toLong() + 1).msecs(), validProfile, insulin).iob).isEqualTo(0.0)
        // no IOB for invalid record
        bolus.isValid = false
        assertThat(bolus.iobCalc(now + T.hours(1).msecs(), validProfile, insulin).iob).isEqualTo(0.0)

        bolus.isValid = true
        val asResult = AutosensResult()
        // there should zero IOB after now
        assertThat(bolus.iobCalc(now, validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).iob).isWithin(0.01).of(0.0)
        // there should be significant IOB at EB finish
        assertThat(bolus.iobCalc(now + T.hours(1).msecs(), validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).iob).isGreaterThan(0.8)
        // there should be less that 5% after DIA -1
        assertThat(
            bolus.iobCalc(
                now + T.hours(dia.toLong() - 1).msecs(), validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin
            ).iob
        ).isLessThan(0.05)
        // there should be zero after DIA
        assertThat(
            bolus.iobCalc(now + T.hours(dia.toLong() + 1).msecs(), validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).iob
        ).isEqualTo(0.0)
        // no IOB for invalid record
        bolus.isValid = false
        assertThat(bolus.iobCalc(now + T.hours(1).msecs(), validProfile, asResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, true, insulin).iob).isEqualTo(0.0)
    }

    @Test fun isInProgress() {
        val bolus = ExtendedBolus(timestamp = now - 1, amount = 1.0, duration = T.hours(1).msecs())
        Mockito.`when`(dateUtil.now()).thenReturn(now)
        assertThat(bolus.isInProgress(dateUtil)).isTrue()
        Mockito.`when`(dateUtil.now()).thenReturn(now + T.hours(2).msecs())
        assertThat(bolus.isInProgress(dateUtil)).isFalse()
    }

    @Test fun toTemporaryBasal() {
        val bolus = ExtendedBolus(timestamp = now - 1, amount = 1.0, duration = T.hours(1).msecs())
        val tbr = bolus.toTemporaryBasal(validProfile)
        assertThat(tbr.timestamp).isEqualTo(bolus.timestamp)
        assertThat(tbr.duration).isEqualTo(bolus.duration)
        assertThat(tbr.rate).isEqualTo(bolus.rate + validProfile.getBasal(now))
        assertThat(tbr.type).isEqualTo(TemporaryBasal.Type.FAKE_EXTENDED)
    }
}