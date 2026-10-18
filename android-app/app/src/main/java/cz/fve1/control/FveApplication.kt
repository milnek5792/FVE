package cz.fve1.control

import android.app.Application
import cz.fve1.control.data.ChartSampler
import cz.fve1.control.data.ChartStore
import cz.fve1.control.data.SettingsStore
import cz.fve1.control.mqtt.MqttRepository
import cz.fve1.control.mqtt.MqttSessionController

class FveApplication : Application() {
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var mqttRepository: MqttRepository
        private set
    lateinit var chartStore: ChartStore
        private set
    lateinit var chartSampler: ChartSampler
        private set

    private lateinit var mqttSession: MqttSessionController

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        mqttRepository = MqttRepository()
        chartStore = ChartStore(this)
        chartSampler = ChartSampler(chartStore, mqttRepository)
        mqttSession = MqttSessionController(mqttRepository)
        mqttSession.start()
    }
}
