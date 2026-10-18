package cz.fve1.control.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import cz.fve1.control.data.MqttSettings
import cz.fve1.control.data.SettingsStore
import cz.fve1.control.screen.MqttLinkStatus
import cz.fve1.control.screen.TelePatch
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

sealed interface MqttEvent {
    data class Status(val status: MqttLinkStatus, val error: String = "") : MqttEvent
    data class Tele(val patch: TelePatch) : MqttEvent
}

enum class PublishResult { Ok, Fail }

class MqttRepository {
    private val listeners = AtomicReference<((MqttEvent) -> Unit)?>(null)
    private val clientRef = AtomicReference<Mqtt3AsyncClient?>(null)
    private val prefixRef = AtomicReference("fve")
    private val userStopped = AtomicBoolean(true)
    private val sessionGen = AtomicInteger(0)
    private val lastStatus = AtomicReference(MqttLinkStatus.Idle)

    fun setListener(listener: ((MqttEvent) -> Unit)?) {
        listeners.set(listener)
    }

    fun isConnected(): Boolean = clientRef.get()?.state?.isConnected == true

    fun sessionActive(): Boolean {
        if (userStopped.get()) return false
        val status = lastStatus.get()
        return status == MqttLinkStatus.Connected || status == MqttLinkStatus.Connecting
    }

    fun connect(settings: MqttSettings) {
        killClient()
        val gen = sessionGen.incrementAndGet()
        userStopped.set(false)

        val prefix = SettingsStore.normalizePrefix(settings.prefix)
        prefixRef.set(prefix)
        emit(MqttEvent.Status(MqttLinkStatus.Connecting))

        val endpoint = parseEndpoint(MqttSettings.normalizeBrokerUrl(settings.host))
        if (endpoint == null) {
            emit(MqttEvent.Status(MqttLinkStatus.Error, "Neplatná adresa brokeru"))
            return
        }

        val clientId = "Fve1Android_" + UUID.randomUUID().toString().take(8)
        var builder = MqttClient.builder()
            .useMqttVersion3()
            .identifier(clientId)
            .serverHost(endpoint.host)
            .serverPort(endpoint.port)
            .automaticReconnectWithDefaultConfig()
            .addConnectedListener {
                if (!isCurrent(gen)) return@addConnectedListener
                emit(MqttEvent.Status(MqttLinkStatus.Connected))
                subscribeAll(gen)
            }
            .addDisconnectedListener { ctx ->
                if (!isCurrent(gen)) return@addDisconnectedListener
                if (userStopped.get()) {
                    if (lastStatus.get() != MqttLinkStatus.Error) {
                        emit(MqttEvent.Status(MqttLinkStatus.Idle))
                    }
                    return@addDisconnectedListener
                }
                if (ctx.source != MqttDisconnectSource.USER) {
                    emit(MqttEvent.Status(MqttLinkStatus.Connecting, "Obnovuji spojení…"))
                }
            }

        if (endpoint.ssl) {
            builder = builder.sslWithDefaultConfig()
        }
        if (endpoint.websocket) {
            builder = builder.webSocketConfig()
                .serverPath(endpoint.path)
                .applyWebSocketConfig()
        }

        val client = builder.buildAsync()
        clientRef.set(client)

        var connectBuilder = client.connectWith()
            .cleanSession(true)
            .keepAlive(60)
        if (settings.user.isNotBlank()) {
            connectBuilder = connectBuilder.simpleAuth()
                .username(settings.user)
                .password(settings.password.toByteArray(StandardCharsets.UTF_8))
                .applySimpleAuth()
        }

        connectBuilder.send()
            .whenComplete { _, err ->
                if (err != null && isCurrent(gen) && !userStopped.get()) {
                    emit(MqttEvent.Status(MqttLinkStatus.Error, friendlyError(err)))
                }
            }
    }

    fun disconnect(notifyIdle: Boolean = true) {
        killClient()
        if (notifyIdle) {
            emit(MqttEvent.Status(MqttLinkStatus.Idle))
        }
    }

    fun publishCommand(payload: String): PublishResult {
        val client = clientRef.get()
        if (client?.state?.isConnected != true) return PublishResult.Fail
        val topic = "${prefixRef.get()}/prikazy"
        client.publishWith()
            .topic(topic)
            .payload(payload.toByteArray(StandardCharsets.UTF_8))
            .qos(MqttQos.AT_LEAST_ONCE)
            .send()
        return PublishResult.Ok
    }

    private fun killClient() {
        userStopped.set(true)
        sessionGen.incrementAndGet()
        val client = clientRef.getAndSet(null) ?: return
        runCatching { client.disconnect() }
    }

    private fun isCurrent(gen: Int): Boolean = sessionGen.get() == gen

    private fun subscribeAll(gen: Int) {
        val client = clientRef.get() ?: return
        for (suffix in SUBSCRIBE_FILTERS) {
            client.subscribeWith()
                .topicFilter("${prefixRef.get()}/$suffix")
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback(::onPublish)
                .send()
                .whenComplete { _, _ ->
                    if (!isCurrent(gen)) return@whenComplete
                }
        }
    }

    private fun onPublish(publish: Mqtt3Publish) {
        val topicName = publish.topic.toString()
        val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8).trim()
        handleMessage(topicName, payload)
    }

    private fun handleMessage(fullTopic: String, raw: String) {
        val base = "${prefixRef.get()}/"
        if (!fullTopic.startsWith(base)) return
        val rel = fullTopic.removePrefix(base)
        val patch = when (rel) {
            "mereni/totalEnergy" -> TelePatch(totalEnergy = "${format0(raw)} kWh")
            "mereni/actualMonthEnergy" -> TelePatch(monthEnergy = "${format0(raw)} kWh")
            "mereni/realPower" -> TelePatch(realPower = "${format0(raw)} W")
            "mereni/fvePower" -> {
                val n = raw.toFloatOrNull()
                TelePatch(
                    fvePower = if (n != null) n.roundToInt().toString() else raw,
                    fvePowerNum = n,
                )
            }
            "mereni/dailyEnergy" -> TelePatch(dailyEnergy = "$raw kWh")
            "stav/rele1" -> TelePatch(rele1On = raw.equals("true", ignoreCase = true))
            "stav/rele2" -> TelePatch(rele2On = raw.equals("true", ignoreCase = true))
            else -> null
        } ?: return
        emit(MqttEvent.Tele(patch))
    }

    private fun format0(x: String): String {
        val n = x.toFloatOrNull() ?: return x
        return n.roundToInt().toString()
    }

    private fun emit(event: MqttEvent) {
        if (event is MqttEvent.Status) {
            lastStatus.set(event.status)
        }
        listeners.get()?.invoke(event)
    }

    private data class Endpoint(
        val host: String,
        val port: Int,
        val path: String,
        val ssl: Boolean,
        val websocket: Boolean,
    )

    private fun parseEndpoint(rawUrl: String): Endpoint? {
        return try {
            val uri = URI(rawUrl.trim())
            val scheme = (uri.scheme ?: "").lowercase()
            val host = uri.host ?: return null
            val websocket = scheme == "ws" || scheme == "wss"
            val ssl = scheme == "wss" || scheme == "mqtts" || scheme == "ssl" || scheme == "mqtt+ssl"
            val defaultPort = when (scheme) {
                "wss" -> 443
                "ws" -> 80
                "mqtts", "ssl", "mqtt+ssl" -> 8883
                else -> 1883
            }
            val port = if (uri.port > 0) uri.port else defaultPort
            val path = (uri.rawPath ?: "mqtt")
                .trim()
                .trimStart('/')
                .ifBlank { "mqtt" }
            Endpoint(host, port, path, ssl, websocket)
        } catch (_: Exception) {
            null
        }
    }

    private fun friendlyError(err: Throwable): String {
        var cur: Throwable? = err
        while (cur?.cause != null && cur.cause !== cur) {
            cur = cur.cause
        }
        val msg = (cur?.message ?: err.message).orEmpty()
        return when {
            msg.contains("WebSocket", ignoreCase = true) ->
                "WebSocket selhal — použij mqtts://…:8883"
            msg.contains("SSL", ignoreCase = true) ||
                msg.contains("TLS", ignoreCase = true) ||
                msg.contains("certificate", ignoreCase = true) ->
                "TLS chyba při spojení s brokerem"
            msg.contains("UnknownHost", ignoreCase = true) ||
                msg.contains("unable to resolve", ignoreCase = true) ->
                "DNS: host brokeru nenalezen"
            msg.contains("timeout", ignoreCase = true) ||
                msg.contains("Timed out", ignoreCase = true) ->
                "Timeout připojení k brokeru"
            msg.contains("Not authorized", ignoreCase = true) ||
                msg.contains("Bad user", ignoreCase = true) ||
                msg.contains("CONNACK", ignoreCase = true) ->
                "Odmítnuto brokerem (uživatel/heslo?)"
            msg.isBlank() -> "Chyba MQTT"
            else -> msg.take(120)
        }
    }

    companion object {
        private val SUBSCRIBE_FILTERS = listOf(
            "mereni/totalEnergy",
            "mereni/actualMonthEnergy",
            "mereni/realPower",
            "mereni/fvePower",
            "mereni/dailyEnergy",
            "stav/rele1",
            "stav/rele2",
        )
    }
}
