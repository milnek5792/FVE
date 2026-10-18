package cz.fve1.control.data

import cz.fve1.control.mqtt.MqttEvent
import cz.fve1.control.mqtt.MqttRepository
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Vzorkuje denní výkon FVE i když není otevřená Activity (MQTT běží s procesem).
 */
class ChartSampler(
    private val chartStore: ChartStore,
    mqtt: MqttRepository,
) {
    private val points = CopyOnWriteArrayList(chartStore.loadToday())
    private val lastSampleTime = AtomicLong(points.lastOrNull()?.t ?: 0L)
    private val listeners = CopyOnWriteArrayList<(List<ChartPoint>) -> Unit>()

    init {
        mqtt.addListener { event ->
            if (event !is MqttEvent.Tele) return@addListener
            val value = event.patch.fvePowerNum ?: return@addListener
            addSample(value)
        }
    }

    fun snapshot(): List<ChartPoint> = points.toList()

    fun addUiListener(listener: (List<ChartPoint>) -> Unit) {
        listeners.add(listener)
        listener(snapshot())
    }

    fun removeUiListener(listener: (List<ChartPoint>) -> Unit) {
        listeners.remove(listener)
    }

    private fun addSample(value: Float) {
        if (!isDaytime()) return
        val now = System.currentTimeMillis()
        val last = lastSampleTime.get()
        if (now - last < SAMPLE_INTERVAL_MS && points.isNotEmpty()) {
            points[points.lastIndex] = ChartPoint(points.last().t, value)
        } else {
            lastSampleTime.set(now)
            points.add(ChartPoint(now, value))
        }
        // Nový den → reset
        val keyDay = todayKey()
        if (points.firstOrNull()?.let { dayKey(it.t) } != keyDay) {
            points.clear()
            points.add(ChartPoint(now, value))
            lastSampleTime.set(now)
        }
        val copy = points.toList()
        chartStore.saveToday(copy)
        for (l in listeners) l(copy)
    }

    private fun isDaytime(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h in 6 until 21
    }

    private fun todayKey(): String = dayKey(System.currentTimeMillis())

    private fun dayKey(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
        )
    }

    companion object {
        private const val SAMPLE_INTERVAL_MS = 60_000L
    }
}
