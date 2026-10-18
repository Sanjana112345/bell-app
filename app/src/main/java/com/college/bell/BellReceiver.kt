package com.college.bell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDateTime

/**
 * The one entry point the system uses to wake this app up:
 *  - ACTION_RING        the exact alarm we set has come due
 *  - BOOT_COMPLETED     the tablet restarted, so re-arm
 *  - MY_PACKAGE_REPLACED the app was updated, so re-arm
 *  - TIME_SET / TIMEZONE_CHANGED  the clock moved, so re-arm
 */
class BellReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RING = "com.college.bell.RING"
        private const val TAG = "Bell/Receiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received ${intent.action}")

        if (intent.action == ACTION_RING) {
            val now = LocalDateTime.now()
            val due = nextDueNow(context, now)

            if (due != null) {
                BellService.ring(context, due.label, due.soundUri, due.durationSec, manual = false)
            } else {
                Log.w(TAG, "Alarm fired but no bell matched $now — schedule changed underneath us.")
            }

            // Chain the next one. Search from a few seconds ahead so the bell that
            // just rang cannot be picked again.
            Scheduler.armNext(context, now.plusSeconds(5))
        } else {
            Scheduler.armNext(context)
        }
    }

    /**
     * Finds the bell this alarm was meant for. Matches on the minute rather than the
     * exact second, because an alarm can land a moment late.
     */
    private fun nextDueNow(context: Context, now: LocalDateTime): BellEvent? {
        val plan = planFor(Store.load(context), now.toLocalDate())
        return plan.events.firstOrNull { e ->
            val t = parseTime(e.time) ?: return@firstOrNull false
            val diff = java.time.Duration.between(LocalDateTime.of(now.toLocalDate(), t), now)
            // fired on time, or up to 2 minutes late (device was briefly busy)
            !diff.isNegative && diff.toMinutes() < 2
        }
    }
}
