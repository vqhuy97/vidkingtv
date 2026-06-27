package com.vidkingtv.app

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import java.io.ByteArrayInputStream

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var doubleBackToExit = false
    private var blockedCount = 0

    // ═══ Comprehensive ad domain list ═══
    private val adDomains = listOf(
        // Vidking's ad partner
        "dolesdao.com", "zv.dolesdao.com",
        // Common popunder/popup networks
        "popads.net", "popcash.net", "propellerads.com", "propellerclick.com",
        "hilltopads.net", "adsterra.com", "monetag.com", "clickadu.com",
        "richpush.net", "pushprofit.net", "trafficjunky.com", "exoclick.com",
        "juicyads.com", "revcontent.com", "mgid.com",
        // Google ads
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "pagead2.googlesyndication.com",
        // Tracking
        "google-analytics.com", "googletagmanager.com",
        "scorecardresearch.com", "taboola.com", "outbrain.com",
        // Ad exchanges
        "adnxs.com", "rubiconproject.com", "pubmatic.com", "openx.net",
        "casalemedia.com", "indexexchange.com", "advertising.com",
        "adsrvr.org", "acscdn.com", "criteo.com", "criteo.net",
        // Adult/random ad networks often used by streaming sites
        "stripchat.com", "chaturbate.com", "trafficfactory.biz",
        "tsyndicate.com", "ad-maven.com", "adskeeper.com"
    )

    private val adKeywords = listOf(
        "/ads/", "/ad?", "?ad=", "&ad=", "popunder", "popcash", "popads",
        "click.php", "track.php", "redirect.php", "/pop/", "/ad_"
    )

    private fun isAdUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (adDomains.any { lower.contains(it) }) return true
        if (adKeywords.any { lower.contains(it) }) return true
        return false
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Fullscreen immersive ──
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // ── Disable third-party cookies globally to reduce tracking ──
        CookieManager.getInstance().setAcceptCookie(true)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // ── KEY ad-blocking settings ──
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                // Disable third-party cookies for the WebView
                CookieManager.getInstance().setAcceptThirdPartyCookies(this@apply, false)
                userAgentString = userAgentString + " VidKingTV/1.0"
            }

            // ═══ Layer 1: Block ad requests at network level ═══
            webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null

                    if (isAdUrl(url)) {
                        blockedCount++
                        android.util.Log.d("AdBlock", "BLOCKED: $url")
                        // Return empty 204 response so request "succeeds" but loads nothing
                        return WebResourceResponse(
                            "text/plain", "UTF-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false

                    // Block ad redirects
                    if (isAdUrl(url)) {
                        android.util.Log.d("AdBlock", "Redirect blocked: $url")
                        return true
                    }

                    // Allow known good domains
                    val allowed = listOf(
                        "vidking.net", "themoviedb.org", "tmdb.org",
                        "image.tmdb.org", "api.themoviedb.org",
                        "fonts.googleapis.com", "fonts.gstatic.com"
                    )
                    if (allowed.any { url.contains(it) }) return false

                    // Allow file:// (local HTML)
                    if (url.startsWith("file://")) return false

                    // Block unknown external navigations
                    android.util.Log.d("AdBlock", "Unknown URL blocked: $url")
                    return true
                }

                // ═══ Layer 2: Inject CSS to hide any leaked ad elements ═══
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val css = """
                        var style = document.createElement('style');
                        style.innerHTML = `
                            iframe[src*="dolesdao"], iframe[src*="popads"],
                            iframe[src*="propeller"], iframe[src*="adsterra"],
                            iframe[src*="hilltopads"], iframe[src*="monetag"],
                            div[id*="popunder"], div[class*="popunder"],
                            div[style*="z-index: 2147483647"]:not(.player-overlay):not(#app *) {
                                display: none !important;
                                visibility: hidden !important;
                                opacity: 0 !important;
                                pointer-events: none !important;
                            }
                        `;
                        document.head.appendChild(style);

                        // Override window.open to block popups
                        var realOpen = window.open;
                        window.open = function() { return null; };
                    """.trimIndent()
                    view?.evaluateJavascript(css, null)
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed()
                }
            }

            // ═══ Layer 3: Block popup window creation ═══
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    android.util.Log.d("AdBlock", "Popup window blocked")
                    blockedCount++
                    return false
                }

                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                    return true
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    // Deny all permission requests (ads sometimes ask for notifications)
                    request?.deny()
                }
            }

            setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        }

        setContentView(webView)
        webView.loadUrl("file:///android_asset/vidking-tv.html")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            // ── D-pad navigation: let WebView handle ──
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                return super.onKeyDown(keyCode, event)
            }

            // ── Back: inject Escape to JS first, then double-back to exit ──
            KeyEvent.KEYCODE_BACK -> {
                // Send Escape to JS so detail/player pages can handle it
                webView.evaluateJavascript(
                    """
                    (function() {
                        var ev = new KeyboardEvent('keydown', {key:'Escape', keyCode:27, bubbles:true});
                        document.dispatchEvent(ev);
                        // Return true if app is on home page (let Android handle back)
                        return (typeof S !== 'undefined' && S.page === 'home') ? 'home' : 'handled';
                    })();
                    """.trimIndent()
                ) { result ->
                    val unquoted = result?.replace("\"", "") ?: ""
                    if (unquoted == "home") {
                        // On home page → double-back to exit
                        runOnUiThread {
                            if (doubleBackToExit) {
                                finish()
                            } else {
                                doubleBackToExit = true
                                Toast.makeText(
                                    this@MainActivity,
                                    "Nhấn Back lần nữa để thoát" +
                                            if (blockedCount > 0) " • Đã chặn $blockedCount quảng cáo" else "",
                                    Toast.LENGTH_SHORT
                                ).show()
                                webView.postDelayed({ doubleBackToExit = false }, 2000)
                            }
                        }
                    }
                }
                return true
            }

            // ── Media keys ──
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                webView.evaluateJavascript(
                    "document.querySelector('iframe.player-frame')?.contentWindow?.postMessage('toggle','*');",
                    null
                )
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        webView.onPause()
        webView.pauseTimers()
        super.onPause()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }
}
