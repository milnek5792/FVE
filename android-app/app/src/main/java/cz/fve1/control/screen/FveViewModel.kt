package cz.fve1.control.screen

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.fve1.control.data.ChartPoint
import cz.fve1.control.data.ChartSampler
import cz.fve1.control.data.MqttSettings
import cz.fve1.control.data.SettingsStore
import cz.fve1.control.mqtt.MqttEvent
import cz.fve1.control.mqtt.MqttRepository
import cz.fve1.control.mqtt.PublishResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FveViewModel(
    private val settingsStore: SettingsStore,
    private val mqtt: MqttRepository,
    private val chartSampler: ChartSampler,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        AppUiState(chartPoints = chartSampler.snapshot()),
    )
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private var settingsSeeded = false
    private var foregroundConnectJob: Job? = null
    private var diagTickJob: Job? = null

    private val mqttListener: (MqttEvent) -> Unit = { event ->
        when (event) {
            is MqttEvent.Status -> _ui.update {
                it.copy(
                    mqttStatus = event.status,
                    mqttError = event.error,
                    mqttConnected = event.status == MqttLinkStatus.Connected || mqtt.isConnected(),
                    sessionActive = mqtt.sessionActive(),
                )
            }
            is MqttEvent.Tele -> _ui.update {
                it.applyTele(event.patch).copy(
                    mqttConnected = true,
                    sessionActive = mqtt.sessionActive(),
                )
            }
        }
    }

    private val chartListener: (List<ChartPoint>) -> Unit = { points ->
        _ui.update { it.copy(chartPoints = points) }
    }

    private val appLifecycle = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // Stejné jako tlačítko Připojit — až je UI nahoře.
            foregroundConnectJob?.cancel()
            foregroundConnectJob = viewModelScope.launch {
                connectFromStoredSettings(retry = true)
            }
        }
    }

    init {
        mqtt.addListener(mqttListener)
        chartSampler.addUiListener(chartListener)
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycle)
        viewModelScope.launch {
            settingsStore.settings.collect { s ->
                if (settingsSeeded) return@collect
                settingsSeeded = true
                _ui.update { it.copy(draft = s) }
            }
        }
        // Obnova diag řádku (stáří tele) i bez nových MQTT zpráv.
        diagTickJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _ui.update {
                    it.copy(
                        mqttConnected = mqtt.isConnected() || it.mqttStatus == MqttLinkStatus.Connected,
                        sessionActive = mqtt.sessionActive(),
                        diagEpoch = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    /** Auto-connect z úložiště — nezasahuje do rozepsaného draftu v UI. */
    private suspend fun connectFromStoredSettings(retry: Boolean) {
        val s = runCatching { settingsStore.settings.first() }.getOrNull() ?: return
        if (!settingsSeeded) {
            settingsSeeded = true
            _ui.update { it.copy(draft = s) }
        }
        if (!s.autoConnect) return
        if (s.host.isBlank() || s.user.isBlank() || s.password.isBlank()) return

        mqtt.connect(s)
        refreshSession()
        if (!retry) return

        delay(3_500)
        if (!mqtt.isConnected() && s.autoConnect) {
            mqtt.connect(s)
            refreshSession()
        }
    }

    fun selectTab(tab: Int) {
        _ui.update { it.copy(tab = tab.coerceIn(0, 1)) }
    }

    fun updateDraft(host: String, user: String, password: String, prefix: String) {
        _ui.update {
            it.copy(
                draft = it.draft.copy(
                    host = host,
                    user = user,
                    password = password,
                    prefix = prefix,
                ),
            )
        }
    }

    fun connect() {
        val draft = _ui.value.draft
        val settings = draft.copy(
            host = MqttSettings.normalizeBrokerUrl(draft.host),
            autoConnect = true,
        )
        viewModelScope.launch {
            settingsStore.save(settings)
            mqtt.connect(settings)
            refreshSession()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            settingsStore.setAutoConnect(false)
            mqtt.disconnect()
            refreshSession()
        }
    }

    fun resetDaily() {
        when (mqtt.publishCommand("reset_day")) {
            PublishResult.Ok -> _ui.update { it.copy(toast = "Nulování odesláno") }
            PublishResult.Fail -> _ui.update { it.copy(toast = "Nejprve se připojte k brokeru") }
        }
    }

    fun consumeToast() {
        _ui.update { it.copy(toast = null) }
    }

    private fun refreshSession() {
        _ui.update {
            it.copy(
                sessionActive = mqtt.sessionActive(),
                mqttConnected = mqtt.isConnected() || it.mqttStatus == MqttLinkStatus.Connected,
                mqttStatus = mqtt.lastStatus(),
                mqttError = mqtt.lastError(),
            )
        }
    }

    override fun onCleared() {
        diagTickJob?.cancel()
        foregroundConnectJob?.cancel()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycle)
        mqtt.removeListener(mqttListener)
        chartSampler.removeUiListener(chartListener)
        super.onCleared()
    }
}
