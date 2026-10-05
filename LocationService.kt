package com.aman.logbook

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder

class LocationService : Service(), LocationListener {
    private lateinit var lm: LocationManager
    private lateinit var nm: NotificationManager

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        lm = getSystemService(LOCATION_SERVICE) as LocationManager
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("track", "Location tracking", NotificationManager.IMPORTANCE_LOW))
        val n = notif("Tracking चालू है")
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(1, n)
        try {
            Store.lastLat = Double.NaN
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, this)
            Store.tracking = true
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun notif(text: String): Notification =
        Notification.Builder(this, "track")
            .setContentTitle("📍 Location Logbook")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int = START_STICKY

    override fun onLocationChanged(l: Location) {
        val now = System.currentTimeMillis()
        Store.onFix(l.latitude, l.longitude, l.accuracy, now, l.speed)
        val km = Store.days[Store.dayKey(now)]?.km ?: 0.0
        nm.notify(1, notif("आज: " + Store.f1(km) + " km"))
    }

    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}

    override fun onDestroy() {
        try { lm.removeUpdates(this) } catch (e: SecurityException) { }
        Store.tracking = false
        super.onDestroy()
    }
}
