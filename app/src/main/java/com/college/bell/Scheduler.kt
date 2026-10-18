package com.college.bell

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sets ONE alarm at a time: the next bell due, and nothing else.
 *
 * Why not set an alarm for every bell in the day? Because one alarm is one thing to
 * get wrong. When a bell fires, the receiver immediately asks for the next one, so
 * the chain keeps itself going. Re-armed on boot, after any save, and after each ring.
 */
object Scheduler {

    private const val TAG = "Bell/Scheduler"
    private const val REQUEST_CODE = 1001

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BellReceiver::class.java).setAction(BellReceiver.ACTION_RING)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Cancels any pending alarm and sets one for the next bell.
     * [from] exists so the receiver can say "find the next bell after the one that
     * just rang" without depending on the clock being a millisecond ahead.
     */
    fun armNext(context: Context, from: LocalDateTime = LocalDateTime.now()) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        am.cancel(pi)

        val next = nextBell(Store.load(context), from)
        if (next == null) {
            Log.i(TAG, "No upcoming bell — nothing armed.")
            return
        }

        val millis = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // setAlarmClock is the strongest guarantee Android offers: it survives Doze
        // and battery optimisation. It also shows an alarm icon in the status bar,
        // which is a free "the system is armed" indicator for staff.
        am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, openAppIntent(context)), pi)

        Log.i(TAG, "Next bell: ${next.event.label} at ${next.at}")
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
