package com.aman.logbook

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private var cur = Store.dayKey(System.currentTimeMillis())
    private lateinit var tvDate: TextView
    private lateinit var tvKm: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvLive: TextView
    private lateinit var tvMsg: TextView
    private lateinit var btn: Button
    private lateinit var tl: LinearLayout
    private val grey = Color.parseColor("#62807A")
    private val tick = object : Runnable {
        override fun run() { render(); h.postDelayed(this, 3000) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float, color: Int = Color.parseColor("#14302B"), bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        val sv = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        sv.addView(col)
        setContentView(sv)

        col.addView(tv("📍 Location Logbook • लोकेशन लॉगबुक", 19f, bold = true))

        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        tvDate = tv("", 16f, bold = true).apply { gravity = Gravity.CENTER }
        nav.addView(Button(this).apply { text = "‹"; setOnClickListener { shift(-1) } })
        nav.addView(tvDate, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(Button(this).apply { text = "›"; setOnClickListener { shift(1) } })
        nav.addView(Button(this).apply {
            text = "Today"
            setOnClickListener { cur = Store.dayKey(System.currentTimeMillis()); render() }
        })
        col.addView(nav)

        tvKm = tv("0.0 km", 48f, Color.parseColor("#0E7C66"), true)
        tvStats = tv("", 14f, grey)
        btn = Button(this).apply { setOnClickListener { toggle() } }
        tvLive = tv("", 13f, grey)
        tvMsg = tv("Tracking background में भी चलेगी (ऊपर notification दिखेगा)।", 13f, grey)
        col.addView(tvKm)
        col.addView(tvStats)
        col.addView(btn)
        col.addView(tvLive)
        col.addView(tvMsg)

        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv("दिन का logbook", 16f, bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(Button(this).apply {
            text = "Copy log"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("log", Store.logText(cur)))
                Toast.makeText(this@MainActivity, "Copy हो गया", Toast.LENGTH_SHORT).show()
            }
        })
        col.addView(head)
        tl = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tl)
    }

    override fun onResume() { super.onResume(); h.post(tick) }
    override fun onPause() { h.removeCallbacks(tick); super.onPause() }

    private fun shift(n: Int) {
        val c = Calendar.getInstance()
        c.time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(cur)!!
        c.add(Calendar.DATE, n)
        cur = Store.dayKey(c.timeInMillis)
        render()
    }

    private fun toggle() {
        if (Store.tracking) {
            stopService(Intent(this, LocationService::class.java))
            Store.tracking = false
            tvMsg.text = "Tracking रुक गई।"
            render()
            return
        }
        val need = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1) else launch()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) launch()
        else tvMsg.text = "Location permission नहीं मिली। Phone Settings > Apps > Location Logbook > Permissions में Location allow करें।"
    }

    private fun launch() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            tvMsg.text = "GPS बंद है। Phone की Location (GPS) on करें, फिर दोबारा शुरू करें।"
            return
        }
        startForegroundService(Intent(this, LocationService::class.java))
        Store.tracking = true
        tvMsg.text = "Tracking चालू है ✓ (background में भी चलेगी)"
        render()
    }

    private fun rename(s: Stay) {
        val et = EditText(this)
        et.setText(Store.placeName(s.lat, s.lon) ?: "")
        et.hint = "जैसे घर, ऑफिस, लाइब्रेरी"
        AlertDialog.Builder(this).setTitle("जगह का नाम").setView(et)
            .setPositiveButton("Save") { _, _ ->
                val v = et.text.toString().trim()
                Store.places.removeAll { Store.dist(s.lat, s.lon, it.lat, it.lon) <= 120 }
                if (v.isNotEmpty()) Store.places.add(Place(s.lat, s.lon, v))
                Store.save()
                render()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun render() {
        val day = Store.days[cur]
        val today = cur == Store.dayKey(System.currentTimeMillis())
        val live = Store.tracking && today
        val list = if (day == null) emptyList() else Store.shown(day, live)
        tvDate.text = SimpleDateFormat("EEE, d MMM yyyy", Locale("en", "IN"))
            .format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(cur)!!)
        tvKm.text = Store.f1(day?.km ?: 0.0) + " km"
        val span = if (day != null && day.stays.isNotEmpty()) Store.tm(day.stays.first().a) + " – " + Store.tm(day.stays.last().b) else "--"
        val spd = if (live && Store.speedKmh >= 0) Store.speedKmh.toInt().toString() + " km/h" else "--"
        tvStats.text = "Stops: ${list.size}  •  Tracked: $span  •  Speed: $spd"
        btn.text = if (Store.tracking) "■ Tracking रोकें" else "▶ Tracking शुरू करें"
        tvLive.text = if (Store.tracking) Store.live else ""
        tl.removeAllViews()
        if (list.isEmpty()) {
            tl.addView(tv("अभी कोई entry नहीं। Tracking शुरू करें; 5 मिनट से ज़्यादा रुकने पर जगह यहाँ दिखेगी।", 14f, grey))
            return
        }
        list.forEachIndexed { i, s ->
            if (i > 0) tl.addView(tv("   ↓ " + Store.f1(s.cd0 - list[i - 1].cd1) + " km सफ़र", 14f, grey))
            val nm = Store.placeName(s.lat, s.lon)
            val isNow = live && s === day!!.stays.last()
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.parseColor(if (isNow) "#FDEBDD" else "#EAF3F1"))
                setOnClickListener { rename(s) }
            }
            card.addView(tv(nm ?: "📌 " + String.format(Locale.US, "%.4f, %.4f", s.lat, s.lon), 16f, bold = true))
            card.addView(tv(Store.tm(s.a) + " से " + (if (isNow) "अभी तक" else Store.tm(s.b)) + " • " + Store.dur(s.b - s.a) +
                (if (nm == null) " • नाम देने के लिए tap करें" else ""), 14f, grey))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, dp(4), 0, dp(4))
            tl.addView(card, lp)
        }
    }
}
