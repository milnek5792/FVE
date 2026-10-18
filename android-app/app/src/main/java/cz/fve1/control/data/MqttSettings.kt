package cz.fve1.control.data

import java.net.URI

data class MqttSettings(
    val host: String = DEFAULT_BROKER,
    val user: String = DEFAULT_USER,
    val password: String = DEFAULT_PASSWORD,
    val prefix: String = "fve",
    val autoConnect: Boolean = true,
) {
    companion object {
        const val DEFAULT_HOST = "n9e16b3c.ala.eu-central-1.emqxsl.com"

        /** Stejný broker jako ESP32 / PWA, nativní MQTTS (ne WSS). */
        const val DEFAULT_BROKER = "mqtts://$DEFAULT_HOST:8883"

        const val DEFAULT_USER = "FVE1WebApp"
        const val DEFAULT_PASSWORD = "mmVHNn6bSLZ59Tw"

        /**
         * PWA potřebuje wss://host:8084/mqtt.
         * Android používá mqtts://host:8883.
         */
        fun normalizeBrokerUrl(raw: String): String {
            val t = raw.trim()
            if (t.isEmpty()) return DEFAULT_BROKER
            if (!t.contains("://")) {
                val hostPort = t.substringBefore('/').trim()
                if (hostPort.isEmpty()) return DEFAULT_BROKER
                return if (hostPort.contains(':')) {
                    "mqtts://$hostPort"
                } else {
                    "mqtts://$hostPort:8883"
                }
            }
            return try {
                val uri = URI(t)
                val scheme = (uri.scheme ?: "").lowercase()
                val host = uri.host ?: return DEFAULT_BROKER
                when (scheme) {
                    "ws", "wss" -> "mqtts://$host:8883"
                    "mqtts", "ssl" -> {
                        val port = if (uri.port > 0) uri.port else 8883
                        "mqtts://$host:$port"
                    }
                    "mqtt", "tcp" -> {
                        val port = if (uri.port > 0) uri.port else 1883
                        "mqtt://$host:$port"
                    }
                    else -> t
                }
            } catch (_: Exception) {
                DEFAULT_BROKER
            }
        }
    }
}
