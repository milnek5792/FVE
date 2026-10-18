package cz.fve1.control.mqtt

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import cz.fve1.control.data.MqttSettings
import cz.fve1.control.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * MQTT session žije s procesem aplikace — ne s Activity/ViewModel.
 */
class MqttSessionController(
    private val settingsStore: SettingsStore,
    private val mqtt: MqttRepository,
    private val scope: CoroutineScope,
) {
    @Volatile
    private var latest: MqttSettings? = null

    private val lifecycle = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            scope.launch { ensureConnected() }
        }
    }

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycle)
        scope.launch {
            settingsStore.settings.collectLatest { s ->
                latest = s
                if (!s.autoConnect) return@collectLatest
                ensureConnected()
            }
        }
    }

    private suspend fun ensureConnected() {
        val s = latest ?: runCatching { settingsStore.settings.first() }.getOrNull()?.also { latest = it }
            ?: return
        if (!s.autoConnect) return
        if (s.host.isBlank() || s.user.isBlank() || s.password.isBlank()) return
        if (mqtt.isConnected()) return
        if (mqtt.sessionActive()) return
        mqtt.connect(s)
    }
}
