package com.schneider.kiosko

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.schneider.kiosko.databinding.ActivityKioskWebBinding

class KioskWebActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LINK_ID = "extra_link_id"
        const val EXTRA_LINK_LABEL = "extra_link_label"
        const val EXTRA_LINK_URL = "extra_link_url"
        private const val STATE_LINK_ID = "state_link_id"
    }

    private lateinit var binding: ActivityKioskWebBinding
    private var currentLinkId: String? = null
    private var lastHomeHandledAtMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKioskWebBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureImmersiveMode()

        configureWebView()
        configureBackBehavior()
        configureButtons()

        val restored = savedInstanceState?.let { binding.webView.restoreState(it) }
        currentLinkId = savedInstanceState?.getString(STATE_LINK_ID)

        if (restored == null) {
            handleIntent(intent, forceReload = true)
        } else {
            handleIntent(intent, forceReload = false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, forceReload = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
        outState.putString(STATE_LINK_ID, currentLinkId)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_BACK) {
            goToKioskHome()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        binding.webView.stopLoading()
        if (isFinishing) {
            binding.webView.destroy()
        }
        super.onDestroy()
    }

    private fun configureImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun configureWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(false)
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.webLoading.isVisible = newProgress < 100
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val uri = request?.url ?: return false
                if (uri.scheme == "http" || uri.scheme == "https") {
                    return false
                }
                return openExternal(uri)
            }
        }
    }

    private fun configureBackBehavior() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goToKioskHome()
                }
            },
        )
    }

    private fun configureButtons() {
        binding.kioskHomeButton.setOnClickListener { goToKioskHome() }
        binding.kioskCloseButton.setOnClickListener { closeCurrentWebApp() }
    }

    private fun goToKioskHome() {
        val now = SystemClock.uptimeMillis()
        if (now - lastHomeHandledAtMs < 300L) return
        lastHomeHandledAtMs = now

        val movedToExistingHomeTask = runCatching {
            val am = getSystemService(android.app.ActivityManager::class.java)
            val mainTaskId = am.appTasks
                .mapNotNull { task -> task.taskInfo }
                .firstOrNull { info ->
                    info.topActivity?.className == MainActivity::class.java.name ||
                        info.baseActivity?.className == MainActivity::class.java.name
                }
                ?.taskId
            if (mainTaskId != null && mainTaskId != taskId) {
                am.moveTaskToFront(mainTaskId, 0)
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (movedToExistingHomeTask) {
            moveTaskToBack(true)
            return
        }

        val kioskIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        runCatching { startActivity(kioskIntent) }
        moveTaskToBack(true)
    }

    private fun closeCurrentWebApp() {
        finishAndRemoveTask()
    }

    private fun handleIntent(intent: Intent, forceReload: Boolean) {
        val url = KioskWebLink.normalizeUrl(intent.getStringExtra(EXTRA_LINK_URL).orEmpty())
        if (url == null) {
            Toast.makeText(this, R.string.web_link_open_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val requestedLinkId = intent.getStringExtra(EXTRA_LINK_ID)?.ifBlank { null } ?: url
        val requestedLabel = intent.getStringExtra(EXTRA_LINK_LABEL).orEmpty()
        if (requestedLabel.isNotBlank()) {
            title = requestedLabel
        }

        val shouldLoad = forceReload || requestedLinkId != currentLinkId || binding.webView.url.isNullOrBlank()
        currentLinkId = requestedLinkId

        if (shouldLoad) {
            binding.webView.loadUrl(url)
        }
    }

    private fun openExternal(uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
