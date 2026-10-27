package de.abfallheusenstamm.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.Calendar
import java.util.TreeMap

/**
 * Berechnet aus data.json + Einstellungen die nächste Erinnerung und plant sie.
 * Es wird immer nur EIN Alarm geplant; nach dem Auslösen plant der Receiver den nächsten.
 */
object Reminders {
    const val CHANNEL = "abholung"
    const val PREFS = "abfall"
    private const val KEY_SETTINGS = "settings"

    val NAMES = linkedMapOf(
        "rest" to "Restabfall",
        "bio" to "Bioabfall",
        "papier" to "Altpapier",
        "gelb" to "Gelber Sack",
        "gruen" to "Grünschnitt",
        "baum" to "Weihnachtsbäume",
        "schadstoff" to "Schadstoffsammlung",
    )
    private val DEFAULT_ON = mapOf(
        "rest" to true, "bio" to true, "papier" to true, "gelb" to true,
        "gruen" to false, "baum" to true, "schadstoff" to false,
    )

    fun data(ctx: Context): JSONObject =
        JSONObject(ctx.assets.open("data.json").bufferedReader(Charsets.UTF_8).use { it.readText() })

    fun settingsJson(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SETTINGS, null)

    fun saveSettings(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SETTINGS, json).apply()
    }

    /** Alle Abholtermine der Straße: "2026-11-06" -> [rest, gelb, ...] (nur eingeschaltete Arten) */
    fun pickups(ctx: Context): TreeMap<String, MutableList<String>> {
        val out = TreeMap<String, MutableList<String>>()
        val s = settingsJson(ctx)?.let { JSONObject(it) } ?: return out
        val street = s.optString("street", "")
        if (street.isEmpty()) return out
        val types = s.optJSONObject("types")
        val jahre = data(ctx).getJSONObject("jahre")
        for (year in jahre.keys()) {
            val y = jahre.getJSONObject(year)
            val strassen = y.getJSONArray("strassen")
            var zBio: String? = null; var zRest: String? = null; var zGelb: String? = null
            for (i in 0 until strassen.length()) {
                val st = strassen.getJSONArray(i)
                if (st.getString(0) == street) {
                    zBio = st.get(1).toString(); zRest = st.get(2).toString(); zGelb = st.getString(3)
                    break
                }
            }
            if (zBio == null) continue
            val zoneFor = mapOf(
                "rest" to zRest, "papier" to zRest, "bio" to zBio, "baum" to zBio,
                "gelb" to zGelb, "schadstoff" to "alle", "gruen" to "alle",
            )
            val termine = y.getJSONObject("termine")
            for (type in NAMES.keys) {
                val on = if (types != null && types.has(type)) types.optBoolean(type) else DEFAULT_ON[type] == true
                if (!on) continue
                val list = termine.optJSONObject(type)?.optJSONArray(zoneFor[type]) ?: continue
                for (i in 0 until list.length()) out.getOrPut(list.getString(i)) { mutableListOf() }.add(type)
            }
        }
        return out
    }

    private fun reminderTime(ctx: Context): Pair<Int, Int> {
        val t = settingsJson(ctx)?.let { JSONObject(it).optString("time", "18:00") } ?: "18:00"
        val p = t.split(":")
        return Pair(p.getOrNull(0)?.toIntOrNull() ?: 18, p.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    /** Zeitpunkt der Erinnerung für einen Abholtag = Vortag um eingestellte Uhrzeit */
    private fun triggerFor(dateIso: String, h: Int, m: Int): Long {
        val p = dateIso.split("-").map { it.toInt() }
        val c = Calendar.getInstance()
        c.clear()
        c.set(p[0], p[1] - 1, p[2], h, m, 0)
        c.add(Calendar.DAY_OF_MONTH, -1)
        return c.timeInMillis
    }

    /** Nächste Erinnerung planen (oder vorhandene löschen, wenn keine mehr kommt). */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(ctx)
        am.cancel(pi)
        val (h, m) = reminderTime(ctx)
        val now = System.currentTimeMillis()
        val next = pickups(ctx).keys.firstOrNull { triggerFor(it, h, m) > now + 5_000 } ?: return
        val at = triggerFor(next, h, m)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("nextDate", next).apply()
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun alarmIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Wird vom Alarm aufgerufen: Benachrichtigung für den Abholtag zeigen, dann nächsten planen. */
    fun fire(ctx: Context) {
        val date = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("nextDate", null)
        val all = pickups(ctx)
        val types = date?.let { all[it] }
        if (date != null && types != null && types.isNotEmpty()) {
            // nur benachrichtigen, wenn der Abholtag morgen (oder heute, bei Verspätung) ist
            val (h, m) = reminderTime(ctx)
            val late = System.currentTimeMillis() - triggerFor(date, h, m)
            if (late < 20 * 60 * 60 * 1000L) {
                val names = types.map { NAMES[it] ?: it }
                notify(ctx, "Morgen: " + names.joinToString(", "), "Bitte rechtzeitig an die Straße stellen.", 100)
            }
        }
        schedule(ctx)
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Abholerinnerungen", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Erinnerung am Vorabend der Müllabholung"
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    fun notificationsAllowed(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun notify(ctx: Context, title: String, text: String, id: Int) {
        ensureChannel(ctx)
        if (!notificationsAllowed(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            // keine Erlaubnis
        }
    }
}
