package de.abfallheusenstamm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarm ist ausgelöst: Erinnerung zeigen und die nächste planen. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.fire(context)
    }
}

/** Nach Neustart, App-Update oder Zeitänderung den Alarm neu planen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.schedule(context)
    }
}
