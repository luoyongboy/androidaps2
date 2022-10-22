package info.nightscout.androidaps.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.SyncNsBolusTransaction
import info.nightscout.androidaps.database.transactions.SyncNsCarbsTransaction
import info.nightscout.androidaps.database.transactions.SyncNsEffectiveProfileSwitchTransaction
import info.nightscout.androidaps.database.transactions.SyncNsProfileSwitchTransaction
import info.nightscout.androidaps.database.transactions.SyncNsTemporaryBasalTransaction
import info.nightscout.androidaps.database.transactions.SyncNsTemporaryTargetTransaction
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.BuildHelper
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.NsClient
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.sync.nsclient.events.EventNSClientNewLog
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toBolus
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toCarbs
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toEffectiveProfileSwitch
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toProfileSwitch
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toTemporaryBasal
import info.nightscout.androidaps.plugins.sync.nsclientV3.extensions.toTemporaryTarget
import info.nightscout.androidaps.receivers.DataWorkerStorage
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.XDripBroadcast
import info.nightscout.sdk.localmodel.treatment.Bolus
import info.nightscout.sdk.localmodel.treatment.Carbs
import info.nightscout.sdk.localmodel.treatment.EffectiveProfileSwitch
import info.nightscout.sdk.localmodel.treatment.ProfileSwitch
import info.nightscout.sdk.localmodel.treatment.TemporaryBasal
import info.nightscout.sdk.localmodel.treatment.TemporaryTarget
import info.nightscout.sdk.localmodel.treatment.Treatment
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ProcessTreatmentsWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Inject lateinit var xDripBroadcast: XDripBroadcast

    override fun doWork(): Result {
        @Suppress("UNCHECKED_CAST")
        val treatments = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as List<Treatment>?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        var ret = Result.success()
        var latestDateInReceivedData = 0L
        val processed = HashMap<String, Long>()

        for (treatment in treatments) {
            aapsLogger.debug(LTag.DATABASE, "Received NS treatment: $treatment")

            //Find latest date in treatment
            val mills = treatment.date
            if (mills != 0L && mills < dateUtil.now())
                if (mills > latestDateInReceivedData) latestDateInReceivedData = mills

            when (treatment) {
                is Bolus                  -> {
                    if (sp.getBoolean(R.string.key_ns_receive_insulin, false) || config.NSCLIENT) {
                        repository.runTransactionForResult(SyncNsBolusTransaction(treatment.toBolus()))
                            .doOnError {
                                aapsLogger.error(LTag.DATABASE, "Error while saving bolus", it)
                                ret = Result.failure(workDataOf("Error" to it.toString()))
                            }
                            .blockingGet()
                            .also { result ->
                                result.inserted.forEach {
                                    uel.log(
                                        UserEntry.Action.BOLUS, UserEntry.Sources.NSClient, it.notes,
                                        ValueWithUnit.Timestamp(it.timestamp),
                                        ValueWithUnit.Insulin(it.amount)
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Inserted bolus $it")
                                    processed[Bolus::class.java.simpleName] = (processed[Bolus::class.java.simpleName] ?: 0) + 1
                                }
                                result.invalidated.forEach {
                                    uel.log(
                                        UserEntry.Action.BOLUS_REMOVED, UserEntry.Sources.NSClient,
                                        ValueWithUnit.Timestamp(it.timestamp),
                                        ValueWithUnit.Insulin(it.amount)
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Invalidated bolus $it")
                                    processed[Bolus::class.java.simpleName] = (processed[Bolus::class.java.simpleName] ?: 0) + 1
                                }
                                result.updatedNsId.forEach {
                                    aapsLogger.debug(LTag.DATABASE, "Updated nsId of bolus $it")
                                    processed[Bolus::class.java.simpleName] = (processed[Bolus::class.java.simpleName] ?: 0) + 1
                                }
                                result.updated.forEach {
                                    aapsLogger.debug(LTag.DATABASE, "Updated amount of bolus $it")
                                    processed[Bolus::class.java.simpleName] = (processed[Bolus::class.java.simpleName] ?: 0) + 1
                                }
                            }
                    }
                }

                is Carbs                  -> {
                    if (sp.getBoolean(R.string.key_ns_receive_carbs, false) || config.NSCLIENT) {
                        repository.runTransactionForResult(SyncNsCarbsTransaction(treatment.toCarbs()))
                            .doOnError {
                                aapsLogger.error(LTag.DATABASE, "Error while saving carbs", it)
                                ret = Result.failure(workDataOf("Error" to it.toString()))
                            }
                            .blockingGet()
                            .also { result ->
                                result.inserted.forEach {
                                    uel.log(
                                        UserEntry.Action.CARBS, UserEntry.Sources.NSClient, it.notes,
                                        ValueWithUnit.Timestamp(it.timestamp),
                                        ValueWithUnit.Gram(it.amount.toInt())
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Inserted carbs $it")
                                    processed[Carbs::class.java.simpleName] = (processed[Carbs::class.java.simpleName] ?: 0) + 1
                                }
                                result.invalidated.forEach {
                                    uel.log(
                                        UserEntry.Action.CARBS_REMOVED, UserEntry.Sources.NSClient,
                                        ValueWithUnit.Timestamp(it.timestamp),
                                        ValueWithUnit.Gram(it.amount.toInt())
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Invalidated carbs $it")
                                    processed[Carbs::class.java.simpleName] = (processed[Carbs::class.java.simpleName] ?: 0) + 1
                                }
                                result.updated.forEach {
                                    uel.log(
                                        UserEntry.Action.CARBS, UserEntry.Sources.NSClient, it.notes,
                                        ValueWithUnit.Timestamp(it.timestamp),
                                        ValueWithUnit.Gram(it.amount.toInt())
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Updated carbs $it")
                                    processed[Carbs::class.java.simpleName] = (processed[Carbs::class.java.simpleName] ?: 0) + 1
                                }
                                result.updatedNsId.forEach {
                                    aapsLogger.debug(LTag.DATABASE, "Updated nsId carbs $it")
                                    processed[Carbs::class.java.simpleName] = (processed[Carbs::class.java.simpleName] ?: 0) + 1
                                }
                            }
                    }
                }

                is TemporaryTarget        -> {
                    if (sp.getBoolean(R.string.key_ns_receive_temp_target, false) || config.NSCLIENT) {
                        if (treatment.duration > 0L) {
                            // not ending event
                            if (treatment.targetBottomAsMgdl() < Constants.MIN_TT_MGDL
                                || treatment.targetBottomAsMgdl() > Constants.MAX_TT_MGDL
                                || treatment.targetTopAsMgdl() < Constants.MIN_TT_MGDL
                                || treatment.targetTopAsMgdl() > Constants.MAX_TT_MGDL
                                || treatment.targetBottomAsMgdl() > treatment.targetTopAsMgdl()
                            ) {
                                aapsLogger.debug(LTag.DATABASE, "Ignored TemporaryTarget $treatment")
                                continue
                            }
                        }
                        repository.runTransactionForResult(SyncNsTemporaryTargetTransaction(treatment.toTemporaryTarget()))
                            .doOnError {
                                aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                                ret = Result.failure(workDataOf("Error" to it.toString()))
                            }
                            .blockingGet()
                            .also { result ->
                                result.inserted.forEach { tt ->
                                    uel.log(
                                        UserEntry.Action.TT, UserEntry.Sources.NSClient,
                                        ValueWithUnit.TherapyEventTTReason(tt.reason),
                                        ValueWithUnit.fromGlucoseUnit(tt.lowTarget, Constants.MGDL),
                                        ValueWithUnit.fromGlucoseUnit(tt.highTarget, Constants.MGDL).takeIf { tt.lowTarget != tt.highTarget },
                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryTarget $tt")
                                    processed[TemporaryTarget::class.java.simpleName] = (processed[TemporaryTarget::class.java.simpleName] ?: 0) + 1
                                }
                                result.invalidated.forEach { tt ->
                                    uel.log(
                                        UserEntry.Action.TT_REMOVED, UserEntry.Sources.NSClient,
                                        ValueWithUnit.TherapyEventTTReason(tt.reason),
                                        ValueWithUnit.Mgdl(tt.lowTarget),
                                        ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryTarget $tt")
                                    processed[TemporaryTarget::class.java.simpleName] = (processed[TemporaryTarget::class.java.simpleName] ?: 0) + 1
                                }
                                result.ended.forEach { tt ->
                                    uel.log(
                                        UserEntry.Action.CANCEL_TT, UserEntry.Sources.NSClient,
                                        ValueWithUnit.TherapyEventTTReason(tt.reason),
                                        ValueWithUnit.Mgdl(tt.lowTarget),
                                        ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Updated TemporaryTarget $tt")
                                    processed[TemporaryTarget::class.java.simpleName] = (processed[TemporaryTarget::class.java.simpleName] ?: 0) + 1
                                }
                                result.updatedNsId.forEach {
                                    aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryTarget $it")
                                    processed[TemporaryTarget::class.java.simpleName] = (processed[TemporaryTarget::class.java.simpleName] ?: 0) + 1
                                }
                                result.updatedDuration.forEach {
                                    aapsLogger.debug(LTag.DATABASE, "Updated duration TemporaryTarget $it")
                                    processed[TemporaryTarget::class.java.simpleName] = (processed[TemporaryTarget::class.java.simpleName] ?: 0) + 1
                                }
                            }
                    }
                }
                /*
                // Convert back emulated TBR -> EB
                if (eventType == TherapyEvent.Type.TEMPORARY_BASAL.text && json.has("extendedEmulated")) {
                    val ebJson = json.getJSONObject("extendedEmulated")
                    ebJson.put("_id", json.getString("_id"))
                    ebJson.put("isValid", json.getBoolean("isValid"))
                    ebJson.put("mills", mills)
                    json = ebJson
                    eventType = JsonHelper.safeGetString(json, "eventType")
                    virtualPumpPlugin.fakeDataDetected = true
                }
                */
                is TemporaryBasal         -> {
                    if (buildHelper.isEngineeringMode() && sp.getBoolean(R.string.key_ns_receive_tbr_eb, false) || config.NSCLIENT) {
                        repository.runTransactionForResult(SyncNsTemporaryBasalTransaction(treatment.toTemporaryBasal()))
                            .doOnError {
                                aapsLogger.error(LTag.DATABASE, "Error while saving temporary basal", it)
                                ret = Result.failure(workDataOf("Error" to it.toString()))
                            }
                            .blockingGet()
                            .also { result ->
                                result.inserted.forEach {
                                    uel.log(
                                        UserEntry.Action.TEMP_BASAL, UserEntry.Sources.NSClient,
                                        ValueWithUnit.Timestamp(it.timestamp),
                                        if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Inserted TemporaryBasal $it")
                                    processed[TemporaryBasal::class.java.simpleName] = (processed[TemporaryBasal::class.java.simpleName] ?: 0) + 1
                                }
                                result.invalidated.forEach {
                                    uel.log(
                                        UserEntry.Action.TEMP_BASAL_REMOVED, UserEntry.Sources.NSClient,
                                        ValueWithUnit.Timestamp(it.timestamp),
                                        if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Invalidated TemporaryBasal $it")
                                    processed[TemporaryBasal::class.java.simpleName] = (processed[TemporaryBasal::class.java.simpleName] ?: 0) + 1
                                }
                                result.ended.forEach {
                                    uel.log(
                                        UserEntry.Action.CANCEL_TEMP_BASAL, UserEntry.Sources.NSClient,
                                        ValueWithUnit.Timestamp(it.timestamp),
                                        if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                    )
                                    aapsLogger.debug(LTag.DATABASE, "Ended TemporaryBasal $it")
                                    processed[TemporaryBasal::class.java.simpleName] = (processed[TemporaryBasal::class.java.simpleName] ?: 0) + 1
                                }
                                result.updatedNsId.forEach {
                                    aapsLogger.debug(LTag.DATABASE, "Updated nsId TemporaryBasal $it")
                                    processed[TemporaryBasal::class.java.simpleName] = (processed[TemporaryBasal::class.java.simpleName] ?: 0) + 1
                                }
                                result.updatedDuration.forEach {
                                    aapsLogger.debug(LTag.DATABASE, "Updated duration TemporaryBasal $it")
                                    processed[TemporaryBasal::class.java.simpleName] = (processed[TemporaryBasal::class.java.simpleName] ?: 0) + 1
                                }
                            }
                    }
                }

                is EffectiveProfileSwitch -> {
                    if (sp.getBoolean(R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT) {
                        treatment.toEffectiveProfileSwitch(dateUtil)?.let { effectiveProfileSwitch ->
                            repository.runTransactionForResult(SyncNsEffectiveProfileSwitchTransaction(effectiveProfileSwitch))
                                .doOnError {
                                    aapsLogger.error(LTag.DATABASE, "Error while saving EffectiveProfileSwitch", it)
                                    ret = Result.failure(workDataOf("Error" to it.toString()))
                                }
                                .blockingGet()
                                .also { result ->
                                    result.inserted.forEach {
                                        uel.log(
                                            UserEntry.Action.PROFILE_SWITCH, UserEntry.Sources.NSClient,
                                            ValueWithUnit.Timestamp(it.timestamp)
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Inserted EffectiveProfileSwitch $it")
                                        processed[EffectiveProfileSwitch::class.java.simpleName] = (processed[EffectiveProfileSwitch::class.java.simpleName] ?: 0) + 1
                                    }
                                    result.invalidated.forEach {
                                        uel.log(
                                            UserEntry.Action.PROFILE_SWITCH_REMOVED, UserEntry.Sources.NSClient,
                                            ValueWithUnit.Timestamp(it.timestamp)
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Invalidated EffectiveProfileSwitch $it")
                                        processed[EffectiveProfileSwitch::class.java.simpleName] = (processed[EffectiveProfileSwitch::class.java.simpleName] ?: 0) + 1
                                    }
                                    result.updatedNsId.forEach {
                                        aapsLogger.debug(LTag.DATABASE, "Updated nsId EffectiveProfileSwitch $it")
                                        processed[EffectiveProfileSwitch::class.java.simpleName] = (processed[EffectiveProfileSwitch::class.java.simpleName] ?: 0) + 1
                                    }
                                }
                        }
                    }
                }

                is ProfileSwitch          -> {
                    if (sp.getBoolean(R.string.key_ns_receive_profile_switch, false) || config.NSCLIENT) {
                        treatment.toProfileSwitch(activePlugin, dateUtil)?.let { profileSwitch ->
                            repository.runTransactionForResult(SyncNsProfileSwitchTransaction(profileSwitch))
                                .doOnError {
                                    aapsLogger.error(LTag.DATABASE, "Error while saving ProfileSwitch", it)
                                    ret = Result.failure(workDataOf("Error" to it.toString()))
                                }
                                .blockingGet()
                                .also { result ->
                                    result.inserted.forEach {
                                        uel.log(
                                            UserEntry.Action.PROFILE_SWITCH, UserEntry.Sources.NSClient,
                                            ValueWithUnit.Timestamp(it.timestamp)
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Inserted ProfileSwitch $it")
                                        processed[ProfileSwitch::class.java.simpleName] = (processed[ProfileSwitch::class.java.simpleName] ?: 0) + 1
                                    }
                                    result.invalidated.forEach {
                                        uel.log(
                                            UserEntry.Action.PROFILE_SWITCH_REMOVED, UserEntry.Sources.NSClient,
                                            ValueWithUnit.Timestamp(it.timestamp)
                                        )
                                        aapsLogger.debug(LTag.DATABASE, "Invalidated ProfileSwitch $it")
                                        processed[ProfileSwitch::class.java.simpleName] = (processed[ProfileSwitch::class.java.simpleName] ?: 0) + 1
                                    }
                                    result.updatedNsId.forEach {
                                        aapsLogger.debug(LTag.DATABASE, "Updated nsId ProfileSwitch $it")
                                        processed[ProfileSwitch::class.java.simpleName] = (processed[ProfileSwitch::class.java.simpleName] ?: 0) + 1
                                    }
                                }
                        }
                    }
                }
            }
            /*
                        when {
                            insulin > 0 || carbs > 0                                                    -> Any()

                            eventType == TherapyEvent.Type.BOLUS_WIZARD.text                            ->
                                bolusCalculatorResultFromJson(json)?.let { bolusCalculatorResult ->
                                    repository.runTransactionForResult(SyncNsBolusCalculatorResultTransaction(bolusCalculatorResult))
                                        .doOnError {
                                            aapsLogger.error(LTag.DATABASE, "Error while saving BolusCalculatorResult", it)
                                            ret = Result.failure(workDataOf("Error" to it.toString()))
                                        }
                                        .blockingGet()
                                        .also { result ->
                                            result.inserted.forEach {
                                                uel.log(
                                                    Action.BOLUS_CALCULATOR_RESULT, Sources.NSClient,
                                                    ValueWithUnit.Timestamp(it.timestamp),
                                                )
                                                aapsLogger.debug(LTag.DATABASE, "Inserted BolusCalculatorResult $it")
                                            }
                                            result.invalidated.forEach {
                                                uel.log(
                                                    Action.BOLUS_CALCULATOR_RESULT_REMOVED, Sources.NSClient,
                                                    ValueWithUnit.Timestamp(it.timestamp),
                                                )
                                                aapsLogger.debug(LTag.DATABASE, "Invalidated BolusCalculatorResult $it")
                                            }
                                            result.updatedNsId.forEach {
                                                aapsLogger.debug(LTag.DATABASE, "Updated nsId BolusCalculatorResult $it")
                                            }
                                        }
                                } ?: aapsLogger.error("Error parsing BolusCalculatorResult json $json")

                            eventType == TherapyEvent.Type.CANNULA_CHANGE.text ||
                                eventType == TherapyEvent.Type.INSULIN_CHANGE.text ||
                                eventType == TherapyEvent.Type.SENSOR_CHANGE.text ||
                                eventType == TherapyEvent.Type.FINGER_STICK_BG_VALUE.text ||
                                eventType == TherapyEvent.Type.NONE.text ||
                                eventType == TherapyEvent.Type.ANNOUNCEMENT.text ||
                                eventType == TherapyEvent.Type.QUESTION.text ||
                                eventType == TherapyEvent.Type.EXERCISE.text ||
                                eventType == TherapyEvent.Type.NOTE.text ||
                                eventType == TherapyEvent.Type.PUMP_BATTERY_CHANGE.text                 ->
                                if (sp.getBoolean(R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT) {
                                    therapyEventFromJson(json)?.let { therapyEvent ->
                                        repository.runTransactionForResult(SyncNsTherapyEventTransaction(therapyEvent))
                                            .doOnError {
                                                aapsLogger.error(LTag.DATABASE, "Error while saving therapy event", it)
                                                ret = Result.failure(workDataOf("Error" to it.toString()))
                                            }
                                            .blockingGet()
                                            .also { result ->
                                                val action = when (eventType) {
                                                    TherapyEvent.Type.CANNULA_CHANGE.text -> Action.SITE_CHANGE
                                                    TherapyEvent.Type.INSULIN_CHANGE.text -> Action.RESERVOIR_CHANGE
                                                    else                                  -> Action.CAREPORTAL
                                                }
                                                result.inserted.forEach { therapyEvent ->
                                                    uel.log(action, Sources.NSClient,
                                                            therapyEvent.note ?: "",
                                                            ValueWithUnit.Timestamp(therapyEvent.timestamp),
                                                            ValueWithUnit.TherapyEventType(therapyEvent.type),
                                                            ValueWithUnit.fromGlucoseUnit(therapyEvent.glucose ?: 0.0, therapyEvent.glucoseUnit.toString).takeIf { therapyEvent.glucose != null }
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Inserted TherapyEvent $therapyEvent")
                                                }
                                                result.invalidated.forEach { therapyEvent ->
                                                    uel.log(Action.CAREPORTAL_REMOVED, Sources.NSClient,
                                                            therapyEvent.note ?: "",
                                                            ValueWithUnit.Timestamp(therapyEvent.timestamp),
                                                            ValueWithUnit.TherapyEventType(therapyEvent.type),
                                                            ValueWithUnit.fromGlucoseUnit(therapyEvent.glucose ?: 0.0, therapyEvent.glucoseUnit.toString).takeIf { therapyEvent.glucose != null }
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Invalidated TherapyEvent $therapyEvent")
                                                }
                                                result.updatedNsId.forEach {
                                                    aapsLogger.debug(LTag.DATABASE, "Updated nsId TherapyEvent $it")
                                                }
                                                result.updatedDuration.forEach {
                                                    aapsLogger.debug(LTag.DATABASE, "Updated nsId TherapyEvent $it")
                                                }
                                            }
                                    } ?: aapsLogger.error("Error parsing TherapyEvent json $json")
                                }

                            eventType == TherapyEvent.Type.COMBO_BOLUS.text                             ->
                                if (buildHelper.isEngineeringMode() && sp.getBoolean(R.string.key_ns_receive_tbr_eb, false) || config.NSCLIENT) {
                                    extendedBolusFromJson(json)?.let { extendedBolus ->
                                        repository.runTransactionForResult(SyncNsExtendedBolusTransaction(extendedBolus))
                                            .doOnError {
                                                aapsLogger.error(LTag.DATABASE, "Error while saving extended bolus", it)
                                                ret = Result.failure(workDataOf("Error" to it.toString()))
                                            }
                                            .blockingGet()
                                            .also { result ->
                                                result.inserted.forEach {
                                                    uel.log(
                                                        Action.EXTENDED_BOLUS, Sources.NSClient,
                                                        ValueWithUnit.Timestamp(it.timestamp),
                                                        ValueWithUnit.Insulin(it.amount),
                                                        ValueWithUnit.UnitPerHour(it.rate),
                                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Inserted ExtendedBolus $it")
                                                }
                                                result.invalidated.forEach {
                                                    uel.log(
                                                        Action.EXTENDED_BOLUS_REMOVED, Sources.NSClient,
                                                        ValueWithUnit.Timestamp(it.timestamp),
                                                        ValueWithUnit.Insulin(it.amount),
                                                        ValueWithUnit.UnitPerHour(it.rate),
                                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Invalidated ExtendedBolus $it")
                                                }
                                                result.ended.forEach {
                                                    uel.log(
                                                        Action.CANCEL_EXTENDED_BOLUS, Sources.NSClient,
                                                        ValueWithUnit.Timestamp(it.timestamp),
                                                        ValueWithUnit.Insulin(it.amount),
                                                        ValueWithUnit.UnitPerHour(it.rate),
                                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(it.duration).toInt())
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Updated ExtendedBolus $it")
                                                }
                                                result.updatedNsId.forEach {
                                                    aapsLogger.debug(LTag.DATABASE, "Updated nsId ExtendedBolus $it")
                                                }
                                                result.updatedDuration.forEach {
                                                    aapsLogger.debug(LTag.DATABASE, "Updated duration ExtendedBolus $it")
                                                }
                                            }
                                    } ?: aapsLogger.error("Error parsing ExtendedBolus json $json")
                                }

                            eventType == TherapyEvent.Type.APS_OFFLINE.text                             ->
                                if (sp.getBoolean(R.string.key_ns_receive_offline_event, false) && buildHelper.isEngineeringMode() || config.NSCLIENT) {
                                    offlineEventFromJson(json)?.let { offlineEvent ->
                                        repository.runTransactionForResult(SyncNsOfflineEventTransaction(offlineEvent))
                                            .doOnError {
                                                aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                                                ret = Result.failure(workDataOf("Error" to it.toString()))
                                            }
                                            .blockingGet()
                                            .also { result ->
                                                result.inserted.forEach { oe ->
                                                    uel.log(
                                                        Action.LOOP_CHANGE, Sources.NSClient,
                                                        ValueWithUnit.OfflineEventReason(oe.reason),
                                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent $oe")
                                                }
                                                result.invalidated.forEach { oe ->
                                                    uel.log(
                                                        Action.LOOP_REMOVED, Sources.NSClient,
                                                        ValueWithUnit.OfflineEventReason(oe.reason),
                                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Invalidated OfflineEvent $oe")
                                                }
                                                result.ended.forEach { oe ->
                                                    uel.log(
                                                        Action.LOOP_CHANGE, Sources.NSClient,
                                                        ValueWithUnit.OfflineEventReason(oe.reason),
                                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(oe.duration).toInt())
                                                    )
                                                    aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $oe")
                                                }
                                                result.updatedNsId.forEach {
                                                    aapsLogger.debug(LTag.DATABASE, "Updated nsId OfflineEvent $it")
                                                }
                                                result.updatedDuration.forEach {
                                                    aapsLogger.debug(LTag.DATABASE, "Updated duration OfflineEvent $it")
                                                }
                                            }
                                    } ?: aapsLogger.error("Error parsing OfflineEvent json $json")
                                }
                        }
                        if (sp.getBoolean(R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT)
                            if (eventType == TherapyEvent.Type.ANNOUNCEMENT.text) {
                                val date = safeGetLong(json, "mills")
                                val now = System.currentTimeMillis()
                                val enteredBy = JsonHelper.safeGetString(json, "enteredBy", "")
                                val notes = JsonHelper.safeGetString(json, "notes", "")
                                if (date > now - 15 * 60 * 1000L && notes.isNotEmpty()
                                    && enteredBy != sp.getString("careportal_enteredby", "AndroidAPS")
                                ) {
                                    val defaultVal = config.NSCLIENT
                                    if (sp.getBoolean(R.string.key_ns_announcements, defaultVal)) {
                                        val announcement = Notification(Notification.NS_ANNOUNCEMENT, notes, Notification.ANNOUNCEMENT, 60)
                                        rxBus.send(EventNewNotification(announcement))
                                    }
                                }
                            }

                         */
        }
        for (key in processed.keys) rxBus.send(EventNSClientNewLog("PROCESSED", "$key ${processed[key]}", NsClient.Version.V3))
        activePlugin.activeNsClient?.updateLatestTreatmentReceivedIfNewer(latestDateInReceivedData)
//        xDripBroadcast.sendTreatments(treatments)
        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}