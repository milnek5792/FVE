package cz.fve1.control.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class ChartPoint(val t: Long, val v: Float)

/**
 * Denní vzorky výkonu FVE — stejná logika jako PWA (localStorage / SharedPreferences).
 */
class ChartStore(context: Context) {
    private val prefs = context.getSharedPreferences("fve1_chart", Context.MODE_PRIVATE)

    fun loadToday(): MutableList<ChartPoint> {
        val raw = prefs.getString(todayKey(), null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ChartPoint(o.getLong("t"), o.getDouble("v").toFloat())
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveToday(points: List<ChartPoint>) {
        val arr = JSONArray()
        for (p in points) {
            arr.put(
                JSONObject()
                    .put("t", p.t)
                    .put("v", p.v.toDouble()),
            )
        }
        prefs.edit().putString(todayKey(), arr.toString()).apply()
    }

    private fun todayKey(): String {
        val c = Calendar.getInstance()
        val y = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        return "fve1_daily_power_%04d-%02d-%02d".format(y, m, d)
    }
}
