package cz.fve1.control.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.fve1.control.data.ChartPoint
import cz.fve1.control.data.ChartStore
import cz.fve1.control.data.SettingsStore
import cz.fve1.control.mqtt.MqttEvent
import cz.fve1.control.mqtt.MqttRepository
import cz.fve1.control.mqtt.PublishResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

class FveViewModel(
    private val settingsStore: SettingsStore,
    private val mqtt: MqttRepository,
    private val chartStore: ChartStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        AppUiState(chartPoints = chartStore.loadToday()),
    )
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private var chartData = chartStore.loadToday()
    private var lastSampleTime = chartData.lastOrNull()?.t ?: 0L

    init {
        mqtt.setListener { event ->
            when (event) {
                is MqttEvent.Status -> _ui.update {
                    it.copy(mqttStatus = event.status, mqttError = event.error)
                }
                is MqttEvent.Tele -> {
                    _ui.update { it.applyTele(event.patch) }
                    event.patch.fvePowerNum?.let { addChartSample(it) }
                }
            }
        }
        viewModelScope.launch {
            settingsStore.settings.collect { s ->
                _ui.update { it.copy(draft = s) }
            }
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
        viewModelScope.launch {
            settingsStore.save(draft.copy(autoConnect = true))
            mqtt.connect(draft.copy(autoConnect = true))
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            settingsStore.setAutoConnect(false)
            mqtt.disconnect()
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

    private fun addChartSample(value: Float) {
        if (!isDaytime()) return
        val now = System.currentTimeMillis()
        if (now - lastSampleTime < SAMPLE_INTERVAL_MS && chartData.isNotEmpty()) {
            chartData[chartData.lastIndex] = ChartPoint(chartData.last().t, value)
        } else {
            lastSampleTime = now
            chartData.add(ChartPoint(now, value))
        }
        chartStore.saveToday(chartData)
        _ui.update { it.copy(chartPoints = chartData.toList()) }
    }

    private fun isDaytime(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h in 6 until 21
    }

    override fun onCleared() {
        mqtt.setListener(null)
        super.onCleared()
    }

    companion object {
        private const val SAMPLE_INTERVAL_MS = 60_000L
    }
}
