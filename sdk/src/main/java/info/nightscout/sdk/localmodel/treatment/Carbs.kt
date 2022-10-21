package info.nightscout.sdk.localmodel.treatment

import info.nightscout.sdk.localmodel.entry.NsUnits

data class Carbs(
    override val date: Long,
    override val device: String?,
    override val identifier: String,
    override val units: NsUnits?,
    override val srvModified: Long,
    override val srvCreated: Long,
    override val utcOffset: Long,
    override val subject: String?,
    override var isReadOnly: Boolean,
    override val isValid: Boolean,
    override val eventType: EventType,
    override val notes: String?,
    override val pumpId: Long?,
    override val pumpType: String?,
    override val pumpSerial: String?,
    val carbs: Double,
    val duration: Long
) : Treatment