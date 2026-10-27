package com.example.tubelight

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var menuBtn: ImageButton

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var fallbackScript: String? = null
    private var sleepTimer: CountDownTimer? = null

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val chrome = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progress.progress = newProgress
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view == null || customView != null) {
                callback?.onCustomViewHidden()
                return
            }
            customView = view
            customCallback = callback
            root.addView(
                view,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            web.visibility = View.GONE
            menuBtn.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insets().apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        override fun onHideCustomView() {
            val v = customView ?: return
            root.removeView(v)
            customView = null
            customCallback?.onCustomViewHidden()
            customCallback = null
            web.visibility = View.VISIBLE
            if (!isInPictureInPictureMode) menuBtn.visibility = View.VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insets().show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        root = findViewById(R.id.root)
        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        menuBtn = findViewById(R.id.menuBtn)

        setupWebView()
        menuBtn.setOnClickListener { showMenu(it) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> chrome.onHideCustomView()
                    web.canGoBack() -> web.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            web.loadUrl(intent?.data?.toString() ?: HOME)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = USER_AGENT
            setSupportZoom(false)
            builtInZoomControls = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        val script = "window.__vtPrefs=" + prefs.toJson() + ";" + AdBlocker.SCRIPT
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                web, script,
                setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")
            )
        } else {
            fallbackScript = script
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request != null && prefs.blockAds && AdBlocker.isAdRequest(request.url)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request == null || !request.isForMainFrame) return false
                val host = request.url.host ?: return false
                if (AdBlocker.isAppHost(host)) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (e: Exception) {
                    // No app can open this link.
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                fallbackScript?.let { view?.evaluateJavascript(it, null) }
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                pushPrefs()
            }
        }
        web.webChromeClient = chrome
    }

    private fun pushPrefs() {
        web.evaluateJavascript("window.__vt&&window.__vt.set(" + prefs.toJson() + ");", null)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun insets() = WindowCompat.getInsetsController(window, window.decorView)

    /* Menu */

    private fun showMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        val m = pm.menu
        m.add(0, 1, 0, "Playback speed (" + speedLabel(prefs.speed) + ")")
        m.add(0, 2, 1, "Sleep timer")
        m.add(0, 3, 2, "Floating player")
        m.add(0, 4, 3, "Loop one").apply { isCheckable = true; isChecked = prefs.loop }
        m.add(0, 5, 4, "Block ads").apply { isCheckable = true; isChecked = prefs.blockAds }
        m.add(0, 6, 5, "Background play").apply { isCheckable = true; isChecked = prefs.backgroundPlay }
        m.add(0, 7, 6, "Hide Shorts").apply { isCheckable = true; isChecked = prefs.hideShorts }
        m.add(0, 8, 7, "Hide comments").apply { isCheckable = true; isChecked = prefs.hideComments }
        m.add(0, 9, 8, "Reload")
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> chooseSpeed()
                2 -> chooseSleep()
                3 -> enterPip()
                4 -> { prefs.loop = !prefs.loop; pushPrefs(); toast(if (prefs.loop) "Looping this video" else "Loop off") }
                5 -> { prefs.blockAds = !prefs.blockAds; pushPrefs(); web.reload() }
                6 -> { prefs.backgroundPlay = !prefs.backgroundPlay; pushPrefs() }
                7 -> { prefs.hideShorts = !prefs.hideShorts; pushPrefs() }
                8 -> { prefs.hideComments = !prefs.hideComments; pushPrefs() }
                9 -> web.reload()
            }
            true
        }
        pm.show()
    }

    private fun speedLabel(s: Float) = (if (s % 1f == 0f) s.toInt().toString() else s.toString()) + "x"

    private fun chooseSpeed() {
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        AlertDialog.Builder(this)
            .setTitle("Playback speed")
            .setItems(speeds.map { speedLabel(it) }.toTypedArray()) { _, i ->
                prefs.speed = speeds[i]
                pushPrefs()
            }
            .show()
    }

    private fun chooseSleep() {
        val labels = arrayOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "End of video")
        val minutes = intArrayOf(0, 15, 30, 45, 60, -1)
        AlertDialog.Builder(this)
            .setTitle("Sleep timer")
            .setItems(labels) { _, i -> setSleep(minutes[i]) }
            .show()
    }

    private fun setSleep(minutes: Int) {
        sleepTimer?.cancel()
        sleepTimer = null
        prefs.sleepAtEnd = false
        when {
            minutes > 0 -> {
                sleepTimer = object : CountDownTimer(minutes * 60_000L, 1_000L) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.pause();})();", null)
                        stopBackground()
                        toast("Sleep timer: playback stopped")
                    }
                }.start()
                toast("Sleep timer set for $minutes minutes")
            }
            minutes == -1 -> {
                prefs.sleepAtEnd = true
                toast("Playback will stop when this video ends")
            }
            else -> toast("Sleep timer off")
        }
        pushPrefs()
    }

    /* Floating player (picture-in-picture) */

    private fun enterPip() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } else {
            toast("Floating player is not supported on this device")
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        menuBtn.visibility = if (isInPictureInPictureMode || customView != null) View.GONE else View.VISIBLE
        web.evaluateJavascript("window.__vt&&window.__vt.pip(" + isInPictureInPictureMode + ");", null)
    }

    /* Background play */

    private fun startBackground() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))
        } catch (e: Exception) {
            // The system refused to start the service. Playback may pause in the background.
        }
    }

    private fun stopBackground() {
        stopService(Intent(this, PlaybackService::class.java))
    }

    override fun onPause() {
        super.onPause()
        if (prefs.backgroundPlay) startBackground()
        else if (!isInPictureInPictureMode) web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        stopBackground()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { web.loadUrl(it.toString()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        sleepTimer?.cancel()
        stopBackground()
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val HOME = "https://m.youtube.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
