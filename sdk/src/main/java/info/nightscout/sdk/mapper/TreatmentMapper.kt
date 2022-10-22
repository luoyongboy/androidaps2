package info.nightscout.sdk.mapper

import info.nightscout.sdk.localmodel.entry.NsUnits
import info.nightscout.sdk.localmodel.treatment.Bolus
import info.nightscout.sdk.localmodel.treatment.Carbs
import info.nightscout.sdk.localmodel.treatment.EffectiveProfileSwitch
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.ProfileSwitch
import info.nightscout.sdk.localmodel.treatment.TemporaryBasal
import info.nightscout.sdk.localmodel.treatment.TemporaryTarget
import info.nightscout.sdk.localmodel.treatment.Treatment
import info.nightscout.sdk.remotemodel.RemoteTreatment
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@JvmSynthetic
internal fun RemoteTreatment.toTreatment(): Treatment? {
    val treatmentTimestamp = timestamp()
    val ageDays = (System.currentTimeMillis() - treatmentTimestamp) / (24* 3600 * 1000.0)
    when {
        insulin != null && insulin > 0                                  ->
            return Bolus(
                date = treatmentTimestamp,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly ?: false,
                isValid = this.isValid ?: true,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                insulin = this.insulin,
                type = Bolus.BolusType.fromString(this.type),
            )

        carbs != null && carbs > 0                                      ->
            return Carbs(
                date = treatmentTimestamp,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly ?: false,
                isValid = this.isValid ?: true,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                carbs = this.carbs,
                duration = this.duration ?: 0L
            )

        eventType == EventType.TEMPORARY_TARGET                         -> {
            if (treatmentTimestamp == 0L) return null

            this.duration ?: return null
            this.targetBottom ?: return null
            this.targetTop ?: return null

            return TemporaryTarget(
                date = treatmentTimestamp,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly ?: false,
                isValid = this.isValid ?: true,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                duration = this.durationInMilliseconds ?: TimeUnit.MINUTES.toMillis(this.duration),
                targetBottom = this.targetBottom,
                targetTop = this.targetTop,
                reason = TemporaryTarget.Reason.fromString(this.reason)
            )
        }

        eventType == EventType.TEMPORARY_BASAL                          -> {
            if (treatmentTimestamp == 0L) return null

            this.absolute ?: this.percent ?: return null
            this.duration ?: return null
            if (this.duration == 0L && this.durationInMilliseconds == null) return null

            return TemporaryBasal(
                date = treatmentTimestamp,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly ?: false,
                isValid = this.isValid ?: true,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                duration = this.durationInMilliseconds ?: TimeUnit.MINUTES.toMillis(this.duration),
                isAbsolute = this.absolute != null,
                rate = this.absolute ?: (this.percent?.plus(100.0)) ?: 0.0,
                type = TemporaryBasal.Type.fromString(this.type)
            )
        }

        eventType == EventType.NOTE && this.originalProfileName != null -> {
            if (treatmentTimestamp == 0L) return null
            this.profileJson ?: return null
            this.originalCustomizedName ?: return null
            this.originalTimeshift ?: return null
            this.originalPercentage ?: return null
            this.originalDuration ?: return null
            this.originalEnd ?: return null

            return EffectiveProfileSwitch(
                date = treatmentTimestamp,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly ?: false,
                isValid = this.isValid ?: true,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                profileJson = JSONObject(this.profileJson),
                originalProfileName = this.originalProfileName,
                originalCustomizedName = this.originalCustomizedName,
                originalTimeshift = this.originalTimeshift,
                originalPercentage = this.originalPercentage,
                originalDuration = this.originalDuration,
                originalEnd = this.originalEnd
            )
        }

        eventType == EventType.PROFILE_SWITCH                           -> {
            if (treatmentTimestamp == 0L) return null
            this.profile ?: return null

            return ProfileSwitch(
                date = treatmentTimestamp,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly ?: false,
                isValid = this.isValid ?: true,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                profileJson = this.profileJson?.let { JSONObject(this.profileJson) },
                profileName = this.profile,
                originalProfileName = this.originalProfileName,
                originalDuration = this.originalDuration,
                duration = this.duration,
                timeShift = this.timeshift,
                percentage = this.percentage,
            )
        }
    }

    return null
}
