package cz.fve1.control

import android.app.Application
import cz.fve1.control.data.ChartStore
import cz.fve1.control.data.SettingsStore
import cz.fve1.control.mqtt.MqttRepository
import cz.fve1.control.mqtt.MqttSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FveApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settingsStore: SettingsStore
        private set
    lateinit var mqttRepository: MqttRepository
        private set
    lateinit var chartStore: ChartStore
        private set

    private lateinit var mqttSession: MqttSessionController

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        mqttRepository = MqttRepository()
        chartStore = ChartStore(this)
        mqttSession = MqttSessionController(settingsStore, mqttRepository, appScope)
        mqttSession.start()
    }
}
