package com.example.mobilestudio

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.InternalAudioSource
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.library.generic.GenericStream

class StreamService : Service(), ConnectChecker {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
    }

    private var stream: GenericStream? = null
    private var projection: MediaProjection? = null
    private val ui = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_STOP -> stopAll()
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun start(intent: Intent) {
        val channelId = "live"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channelId, "Live", NotificationManager.IMPORTANCE_LOW))
        val notif = Notification.Builder(this, channelId)
            .setContentTitle("Mobile Studio")
            .setContentText("লাইভ চলছে")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val code = intent.getIntExtra("code", 0)
        val data = intent.getParcelableExtra<Intent>("data") ?: return stopAll()
        val url = intent.getStringExtra("url") ?: return stopAll()

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpm.getMediaProjection(code, data)
        projection = mp

        val s = GenericStream(this, this, ScreenSource(applicationContext, mp), InternalAudioSource(mp))
        stream = s
        val w = intent.getIntExtra("w", 1280)
        val h = intent.getIntExtra("h", 720)
        val fps = intent.getIntExtra("fps", 30)
        var kbps = intent.getIntExtra("bitrate", 0)
        if (kbps == 0) { // Auto
            val px = w * h
            kbps = when {
                px >= 2560 * 1440 -> 9000
                px >= 1920 * 1080 -> 4500
                px >= 1280 * 720 -> 3000
                px >= 854 * 480 -> 1500
                else -> 1000
            }
            if (fps > 30) kbps = kbps * 3 / 2
        }
        val videoOk = s.prepareVideo(w, h, kbps * 1000, fps = fps)
        val audioOk = s.prepareAudio(44100, true, 128_000)
        if (videoOk && audioOk) s.startStream(url) else { toast("এনকোডার চালু করা যায়নি"); stopAll() }
    }

    private fun stopAll() {
        stream?.let { if (it.isStreaming) it.stopStream(); it.release() }
        stream = null
        projection?.stop(); projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun toast(s: String) = ui.post { Toast.makeText(applicationContext, s, Toast.LENGTH_SHORT).show() }

    override fun onConnectionStarted(url: String) { toast("সংযোগ হচ্ছে...") }
    override fun onConnectionSuccess() { toast("লাইভ শুরু হয়েছে ✅") }
    override fun onConnectionFailed(reason: String) { toast("সংযোগ ব্যর্থ: $reason"); ui.post { stopAll() } }
    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() { toast("লাইভ বন্ধ") }
    override fun onAuthError() { toast("Stream Key ভুল") }
    override fun onAuthSuccess() {}

    override fun onDestroy() { stopAll(); super.onDestroy() }
}
