package cz.fve1.control.mqtt

/**
 * FVE nechá MQTT běžet s procesem (graf denního výkonu na pozadí).
 * Připojení při otevření řídí ViewModel — stejná cesta jako tlačítko Připojit.
 */
class MqttSessionController(
    @Suppress("unused") private val mqtt: MqttRepository,
) {
    fun start() {
        // no-op: disconnect on background by design skipped (chart sampling)
    }
}
