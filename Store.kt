package com.aman.logbook

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Stay(var lat: Double, var lon: Double, var a: Long, var b: Long, var cd0: Double, var cd1: Double)
class Place(val lat: Double, val lon: Double, val name: String)
class Day(var km: Double = 0.0, val stays: MutableList<Stay> = mutableListOf())

object Store {
    const val RADIUS = 75.0          // meters: same place if within this radius
    const val MIN_STAY = 300_000L    // 5 minutes: shorter pauses are not shown as stops
    private var prefs: SharedPreferences? = null
    val days = HashMap<String, Day>()
    val places = ArrayList<Place>()
    var tracking = false
    var live = ""
    var speedKmh = -1.0
    var lastLat = Double.NaN
    var lastLon = Double.NaN

    fun init(c: Context) {
        if (prefs != null) return
        prefs = c.applicationContext.getSharedPreferences("logbook", Context.MODE_PRIVATE)
        try { parse(JSONObject(prefs!!.getString("data", "{}")!!)) } catch (e: Exception) { }
    }

    private fun parse(o: JSONObject) {
        val d = o.optJSONObject("d") ?: JSONObject()
        for (k in d.keys()) {
            val j = d.getJSONObject(k)
            val day = Day(j.getDouble("km"))
            val s = j.getJSONArray("s")
            for (i in 0 until s.length()) {
                val a = s.getJSONArray(i)
                day.stays.add(Stay(a.getDouble(0), a.getDouble(1), a.getLong(2), a.getLong(3), a.getDouble(4), a.getDouble(5)))
            }
            days[k] = day
        }
        val p = o.optJSONArray("p") ?: JSONArray()
        for (i in 0 until p.length()) {
            val a = p.getJSONArray(i)
            places.add(Place(a.getDouble(0), a.getDouble(1), a.getString(2)))
        }
    }

    fun save() {
        val d = JSONObject()
        for ((k, v) in days) {
            val s = JSONArray()
            for (x in v.stays) s.put(JSONArray(listOf(x.lat, x.lon, x.a, x.b, x.cd0, x.cd1)))
            d.put(k, JSONObject().put("km", v.km).put("s", s))
        }
        val p = JSONArray()
        for (x in places) p.put(JSONArray(listOf(x.lat, x.lon, x.name)))
        prefs?.edit()?.putString("data", JSONObject().put("d", d).put("p", p).toString())?.apply()
    }

    fun dist(a: Double, b: Double, c: Double, d: Double): Double {
        val r = Math.PI / 180
        val x = Math.sin((c - a) * r / 2)
        val y = Math.sin((d - b) * r / 2)
        val h = x * x + Math.cos(a * r) * Math.cos(c * r) * y * y
        return 2 * 6371000.0 * Math.asin(Math.sqrt(h))
    }

    fun dayKey(t: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))
    fun tm(t: Long): String = SimpleDateFormat("h:mm a", Locale("en", "IN")).format(Date(t))
    fun f1(x: Double): String = String.format(Locale.US, "%.1f", x)
    fun dur(ms: Long): String {
        val m = Math.round(ms / 60000.0)
        return if (m < 60) "$m min" else "${m / 60} hr ${m % 60} min"
    }

    fun placeName(lat: Double, lon: Double): String? {
        var best: Place? = null
        var bd = 1e9
        for (p in places) {
            val d = dist(lat, lon, p.lat, p.lon)
            if (d < bd && d <= 120) { bd = d; best = p }
        }
        return best?.name
    }

    fun shown(day: Day, liveLast: Boolean): List<Stay> =
        day.stays.filterIndexed { i, s -> s.b - s.a >= MIN_STAY || i == 0 || (liveLast && i == day.stays.size - 1) }

    fun onFix(lat: Double, lon: Double, acc: Float, t: Long, speed: Float) {
        live = "अभी: " + String.format(Locale.US, "%.5f, %.5f", lat, lon) + " • ±" + acc.toInt() + " m"
        speedKmh = if (speed >= 0) speed * 3.6 else -1.0
        if (acc > 100) return
        val day = days.getOrPut(dayKey(t)) { Day() }
        if (acc <= 50 && !lastLat.isNaN()) {
            val st = dist(lastLat, lastLon, lat, lon)
            if (st >= 10) day.km += st / 1000
        }
        lastLat = lat; lastLon = lon
        val s = day.stays.lastOrNull()
        if (s != null && dist(s.lat, s.lon, lat, lon) <= RADIUS) { s.b = t; s.cd1 = day.km }
        else day.stays.add(Stay(lat, lon, t, t, day.km, day.km))
        save()
    }

    fun logText(key: String): String {
        val day = days[key] ?: return "Location Logbook – $key\nकोई entry नहीं"
        val list = shown(day, false)
        val o = mutableListOf("Location Logbook – $key", "कुल: ${f1(day.km)} km")
        list.forEachIndexed { i, s ->
            if (i > 0) o.add("   ↓ ${f1(s.cd0 - list[i - 1].cd1)} km")
            o.add("${tm(s.a)} – ${tm(s.b)}  " + (placeName(s.lat, s.lon) ?: String.format(Locale.US, "%.5f, %.5f", s.lat, s.lon)))
        }
        return o.joinToString("\n")
    }
}
