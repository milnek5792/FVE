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
    val mqttConnected: Boolean = false,
    val sessionActive: Boolean = false,
    val lastTeleAt: Long = 0L,
    /** Tikání pro obnovu diag řádku (stáří tele). */
    val diagEpoch: Long = 0L,
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
    val settingsStatus: String
        get() = when {
            mqttStatus == MqttLinkStatus.Error -> mqttError.ifBlank { "Chyba MQTT" }
            mqttConnected -> "Připojeno — data živá"
            mqttStatus == MqttLinkStatus.Connecting -> mqttError.ifBlank { "Připojování…" }
            else -> "Odpojeno"
        }

    /** Diagnostický řádek pod stavem (stejný účel jako u Windmi). */
    val diagLine: String
        get() {
            val host = draft.host.removePrefix("mqtts://").removePrefix("mqtt://").take(28)
            val tele = when {
                lastTeleAt <= 0L -> "tele:—"
                else -> {
                    val ageSec = ((System.currentTimeMillis() - lastTeleAt) / 1000L).coerceAtLeast(0)
                    "tele:${ageSec}s"
                }
            }
            val chart = "graf:${chartPoints.size}"
            val st = when (mqttStatus) {
                MqttLinkStatus.Connected -> "ok"
                MqttLinkStatus.Connecting -> "…"
                MqttLinkStatus.Error -> "err"
                MqttLinkStatus.Idle -> "off"
            }
            val err = if (mqttStatus == MqttLinkStatus.Error && mqttError.isNotBlank()) {
                " · ${mqttError.take(40)}"
            } else {
                ""
            }
            return "$st · $host · $tele · $chart$err"
        }

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
        lastTeleAt = System.currentTimeMillis(),
    )

    companion object {
        const val MAX_FVE_POWER_W = 1350f
    }
}
