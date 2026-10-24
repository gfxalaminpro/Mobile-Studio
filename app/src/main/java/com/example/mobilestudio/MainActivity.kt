package com.example.mobilestudio

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var image: ImageView
    private lateinit var video: VideoView
    private lateinit var web: WebView
    private lateinit var label: TextView
    private lateinit var panel: View
    private lateinit var stopBtn: Button
    private lateinit var etUrl: EditText
    private lateinit var etKey: EditText

    private var pdf: PdfRenderer? = null
    private var pdfPage = 0
    private var pendingUrl = ""
    private var portrait = false
    private val resLabels = listOf("360p", "480p", "720p (HD)", "1080p (Full HD)", "1440p (2K)")
    private val resSizes = listOf(640 to 360, 854 to 480, 1280 to 720, 1920 to 1080, 2560 to 1440)
    private val fpsList = listOf(24, 25, 30, 50, 60)
    private val brLabels = listOf("Auto", "1 Mbps", "2.5 Mbps", "4 Mbps", "6 Mbps", "9 Mbps", "12 Mbps")
    private val brValues = listOf(0, 1000, 2500, 4000, 6000, 9000, 12000)
    private lateinit var spRes: Spinner
    private lateinit var spFps: Spinner
    private lateinit var spBr: Spinner
    private lateinit var btnOrient: Button

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { show(it) }
    }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val data = r.data
            if (r.resultCode == RESULT_OK && data != null) {
                val i = Intent(this, StreamService::class.java)
                    .setAction(StreamService.ACTION_START)
                    .putExtra("code", r.resultCode)
                    .putExtra("data", data)
                    .putExtra("url", pendingUrl)
                    .putExtra("portrait", portrait)
                    .putExtra("w", resSizes[spRes.selectedItemPosition].let { if (portrait) it.second else it.first })
                    .putExtra("h", resSizes[spRes.selectedItemPosition].let { if (portrait) it.first else it.second })
                    .putExtra("fps", fpsList[spFps.selectedItemPosition])
                    .putExtra("bitrate", brValues[spBr.selectedItemPosition])
                startForegroundService(i)
                panel.visibility = View.GONE
                stopBtn.visibility = View.VISIBLE
            }
        }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            if (res[Manifest.permission.RECORD_AUDIO] == true) {
                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projectionLauncher.launch(mpm.createScreenCaptureIntent())
            } else toast("অডিও ক্যাপচারের অনুমতি দিতে হবে")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        image = findViewById(R.id.image)
        video = findViewById(R.id.video)
        web = findViewById(R.id.web)
        label = findViewById(R.id.label)
        panel = findViewById(R.id.panel)
        stopBtn = findViewById(R.id.btnStop)
        etUrl = findViewById(R.id.etUrl)
        etKey = findViewById(R.id.etKey)
        web.settings.javaScriptEnabled = true
        btnOrient = findViewById(R.id.btnOrient)
        spRes = findViewById(R.id.spRes); spFps = findViewById(R.id.spFps); spBr = findViewById(R.id.spBitrate)
        fun fill(sp: Spinner, items: List<String>, sel: Int) {
            sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
            sp.setSelection(sel)
        }
        fill(spRes, resLabels, 2)
        fill(spFps, fpsList.map { "$it fps" }, 2)
        fill(spBr, brLabels, 0)
        applyOrientation()
        btnOrient.setOnClickListener { portrait = !portrait; applyOrientation() }

        findViewById<Button>(R.id.btnPick).setOnClickListener {
            pickFile.launch(arrayOf("image/*", "video/*", "audio/*", "application/pdf", "text/html"))
        }
        findViewById<Button>(R.id.btnLive).setOnClickListener { goLive() }
        stopBtn.setOnClickListener {
            startService(Intent(this, StreamService::class.java).setAction(StreamService.ACTION_STOP))
            stopBtn.visibility = View.GONE
            panel.visibility = View.VISIBLE
        }
        // PDF: tap = next page, long press = previous page
        image.setOnClickListener { if (pdf != null) { pdfPage++; renderPdf() } }
        image.setOnLongClickListener { if (pdf != null) { pdfPage--; renderPdf() }; true }
    }

    private fun applyOrientation() {
        requestedOrientation = if (portrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        btnOrient.text = if (portrait) "পোর্ট্রেট" else "ল্যান্ডস্কেপ"
        if (pdf != null) image.post { renderPdf() }
    }

    private fun goLive() {
        val url = etUrl.text.toString().trim().trimEnd('/')
        val key = etKey.text.toString().trim()
        if (url.isEmpty() || key.isEmpty()) { toast("Stream URL ও Key দিন"); return }
        pendingUrl = "$url/$key"
        permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun hideAll() {
        image.visibility = View.GONE; video.visibility = View.GONE
        web.visibility = View.GONE; label.visibility = View.GONE
        video.stopPlayback(); pdf?.close(); pdf = null
    }

    private fun show(uri: Uri) {
        hideAll()
        val type = contentResolver.getType(uri) ?: ""
        when {
            type.startsWith("image/") -> { image.visibility = View.VISIBLE; image.setImageURI(uri) }
            type.startsWith("video/") || type.startsWith("audio/") -> {
                video.visibility = View.VISIBLE
                if (type.startsWith("audio/")) { label.text = "🎵 অডিও চলছে"; label.visibility = View.VISIBLE }
                video.setVideoURI(uri)
                video.setOnPreparedListener { it.isLooping = true; video.start() }
            }
            type == "application/pdf" -> {
                val fd = contentResolver.openFileDescriptor(uri, "r") ?: return
                pdf = PdfRenderer(fd); pdfPage = 0
                image.visibility = View.VISIBLE
                image.post { renderPdf() }
            }
            type.contains("html") -> {
                val html = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
                web.visibility = View.VISIBLE
                web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
            else -> toast("এই ধরনের ফাইল সমর্থিত নয়")
        }
    }

    private fun renderPdf() {
        val r = pdf ?: return
        pdfPage = pdfPage.coerceIn(0, r.pageCount - 1)
        val page = r.openPage(pdfPage)
        val h = if (image.height > 0) image.height else 1080
        val w = h * page.width / page.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        image.setImageBitmap(bmp)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() { pdf?.close(); super.onDestroy() }
}
