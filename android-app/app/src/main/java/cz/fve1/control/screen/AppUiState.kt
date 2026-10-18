package cz.fve1.control.screen

import cz.fve1.control.data.ChartPoint
import cz.fve1.control.data.MqttSettings

enum class MqttLinkStatus { Idle, Connecting, Connected, Error }

data class TelePatch(
    val totalEnergy: String? = null,
    val monthEnergy: String? = null,
    val realPower: String? = null,
    val fvePower: String? = null,
    val fvePowerNum: Float? = null,
    val dailyEnergy: String? = null,
    val rele1On: Boolean? = null,
    val rele2On: Boolean? = null,
)

data class AppUiState(
    val tab: Int = 0,
    val mqttStatus: MqttLinkStatus = MqttLinkStatus.Idle,
    val mqttError: String = "",
    val totalEnergy: String = "-- kWh",
    val monthEnergy: String = "-- kWh",
    val realPower: String = "-- W",
    val fvePower: String = "--",
    val fvePowerPct: Float = 0f,
    val dailyEnergy: String = "-- kWh",
    val rele1On: Boolean = false,
    val rele2On: Boolean = false,
    val chartPoints: List<ChartPoint> = emptyList(),
    val draft: MqttSettings = MqttSettings(),
    val toast: String? = null,
) {
    fun applyTele(patch: TelePatch): AppUiState = copy(
        totalEnergy = patch.totalEnergy ?: totalEnergy,
        monthEnergy = patch.monthEnergy ?: monthEnergy,
        realPower = patch.realPower ?: realPower,
        fvePower = patch.fvePower ?: fvePower,
        fvePowerPct = patch.fvePowerNum?.let { power ->
            ((power / MAX_FVE_POWER_W) * 100f).coerceIn(0f, 100f)
        } ?: fvePowerPct,
        dailyEnergy = patch.dailyEnergy ?: dailyEnergy,
        rele1On = patch.rele1On ?: rele1On,
        rele2On = patch.rele2On ?: rele2On,
    )

    companion object {
        const val MAX_FVE_POWER_W = 1350f
    }
}
