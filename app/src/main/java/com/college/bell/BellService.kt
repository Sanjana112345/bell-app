package com.college.bell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.time.LocalDateTime

/**
 * Plays the bell. Runs as a foreground service so Android cannot kill the process
 * mid-ring; it stops itself the moment the sound is done.
 *
 * ===================================================================
 *  THIS IS THE FILE TO EDIT WHEN THE HARDWARE / IoT RELAY ARRIVES.
 *  Everything about "a bell happened" funnels through trigger() below.
 * ===================================================================
 */
class BellService : Service() {

    companion object {
        private const val TAG = "Bell/Service"
        private const val CHANNEL_ID = "bell_ringing"
        private const val NOTIFICATION_ID = 1

        private const val EXTRA_LABEL = "label"
        private const val EXTRA_SOUND = "sound"
        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_MANUAL = "manual"

        /** True while a bell is sounding. Used to block a second overlapping ring. */
        @Volatile
        var isRinging: Boolean = false
            private set

        private var player: MediaPlayer? = null

        fun ring(
            context: Context,
            label: String,
            soundUri: String?,
            durationSec: Int,
            manual: Boolean,
        ) {
            if (isRinging) {
                Log.w(TAG, "Already ringing — ignoring '$label'.")
                return
            }
            val intent = Intent(context, BellService::class.java)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_SOUND, soundUri)
                .putExtra(EXTRA_DURATION, durationSec)
                .putExtra(EXTRA_MANUAL, manual)
            context.startForegroundService(intent)
        }

        /** Stops the sound now. Does NOT touch any future scheduled bell. */
        fun stop(context: Context) {
            context.stopService(Intent(context, BellService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Bell"
        val soundUri = intent?.getStringExtra(EXTRA_SOUND)
        val duration = intent?.getIntExtra(EXTRA_DURATION, 10) ?: 10
        val manual = intent?.getBooleanExtra(EXTRA_MANUAL, false) ?: false

        startForeground(NOTIFICATION_ID, buildNotification(label))
        isRinging = true

        val failure = trigger(label, soundUri, duration)
        log(label, manual, failure)

        return START_NOT_STICKY
    }

    /**
     * ----------------------------------------------------------------
     *  THE BELL FIRES HERE.
     *
     *  Today that means playing an audio file through whatever the tablet
     *  is plugged into. When the microcontroller / relay board is added
     *  later, add the call to it right below playAudio() — an HTTP POST or
     *  an MQTT publish is a handful of lines, and nothing else in the app
     *  has to change.
     *
     *  Deliberately NOT an interface with one implementation: there is only
     *  one output today, and one function is easier to find and edit than a
     *  class hierarchy.
     * ----------------------------------------------------------------
     *
     * @return null on success, or a short message describing what went wrong.
     */
    private fun trigger(label: String, soundUri: String?, durationSec: Int): String? {
        Log.i(TAG, "RING: $label for ${durationSec}s")

        // ponytail: IoT hook goes here.
        //   e.g. relayClient.post("/ring", durationSec)
        // Keep it beside playAudio so both outputs fire together during the
        // changeover, then delete whichever one is no longer wanted.

        return playAudio(soundUri, durationSec)
    }

    /** @return null on success, or an error message. */
    private fun playAudio(soundUri: String?, durationSec: Int): String? {
        return try {
            val uri = soundUri?.let { Uri.parse(it) }
                ?: Uri.parse("android.resource://$packageName/${R.raw.default_bell}")

            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        // ALARM usage, so the bell still sounds when the tablet is
                        // on silent or Do Not Disturb. This is the whole point.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@BellService, uri)
                // Loop so a short sound still fills the configured duration.
                isLooping = true
                prepare()
                start()
            }

            raiseAlarmVolume()

            // Stop after the configured duration, then shut the service down.
            handler.postDelayed({ stopSelf() }, durationSec.coerceIn(1, 300) * 1000L)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Playback failed", e)
            handler.postDelayed({ stopSelf() }, 2000)
            e.message ?: "Playback failed"
        }
    }

    /** A bell nobody can hear is a failed bell. Push the alarm stream to full. */
    private fun raiseAlarmVolume() {
        runCatching {
            val am = getSystemService(AudioManager::class.java)
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        }
    }

    /** Appends to the ring log, keeping the last 30 days. */
    private fun log(label: String, manual: Boolean, failure: String?) {
        val cfg = Store.load(this)
        val cutoff = LocalDateTime.now().minusDays(30).toString()
        val entry = RingLog(
            at = LocalDateTime.now().withSecond(0).withNano(0).toString(),
            label = label,
            manual = manual,
            failed = failure,
        )
        Store.save(this, cfg.copy(history = (cfg.history + entry).filter { it.at >= cutoff }))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        isRinging = false
        super.onDestroy()
    }

    private fun buildNotification(label: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Bell ringing", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Ringing: $label")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
    }
}
