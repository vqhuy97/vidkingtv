package com.vidkingtv.app

import android.annotation.SuppressLint
import android.app.Activity
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

    private val adDomains = listOf(
        "dolesdao.com", "zv.dolesdao.com",
        "popads.net", "popcash.net", "propellerads.com", "propellerclick.com",
        "hilltopads.net", "adsterra.com", "monetag.com", "clickadu.com",
        "richpush.net", "pushprofit.net", "trafficjunky.com", "exoclick.com",
        "juicyads.com", "revcontent.com", "mgid.com",
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "pagead2.googlesyndication.com",
        "scorecardresearch.com", "taboola.com", "outbrain.com",
        "adnxs.com", "rubiconproject.com", "pubmatic.com", "openx.net",
        "casalemedia.com", "indexexchange.com", "advertising.com",
        "adsrvr.org", "acscdn.com", "criteo.com", "criteo.net",
        "stripchat.com", "chaturbate.com", "trafficfactory.biz",
        "tsyndicate.com", "ad-maven.com", "adskeeper.com",
        "horsedul.com", "fossane.com"
    )

    private val adKeywords = listOf(
        "/ads/", "/ad?", "?ad=", "&ad=", "popunder", "popcash", "popads",
        "click.php", "track.php", "redirect.php", "/pop/", "/ad_"
    )

    // Domains that must ALWAYS be allowed (whitelist takes priority over ad block)
    private val allowedDomains = listOf(
        "vidking.net",
        "themoviedb.org", "tmdb.org", "api.themoviedb.org", "image.tmdb.org",
        "fonts.googleapis.com", "fonts.gstatic.com",
        "google-analytics.com", "googletagmanager.com" // needed by some players
    )

    private fun isAdUrl(url: String): Boolean {
        val lower = url.lowercase()
        // Whitelist first — never block these
        if (allowedDomains.any { lower.contains(it) }) return false
        if (adDomains.any { lower.contains(it) }) return true
        if (adKeywords.any { lower.contains(it) }) return true
        return false
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        CookieManager.getInstance().setAcceptCookie(true)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                // ── Cho phép JS trong file:// truy cập HTTPS resources ──
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                userAgentString = userAgentString + " VidKingTV/1.0"
            }

            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    if (isAdUrl(url)) {
                        blockedCount++
                        android.util.Log.d("AdBlock", "BLOCKED: $url")
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

                    // Always allow file:// (local HTML)
                    if (url.startsWith("file://")) return false

                    // Always allow whitelisted domains
                    if (allowedDomains.any { url.contains(it) }) return false

                    // Block ad redirects
                    if (isAdUrl(url)) {
                        android.util.Log.d("AdBlock", "Redirect blocked: $url")
                        return true
                    }

                    // Block unknown external navigations (prevent hijacking)
                    android.util.Log.d("AdBlock", "External URL blocked: $url")
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Inject CSS + override window.open to kill popup ads
                    view?.evaluateJavascript("""
                        (function() {
                            var style = document.createElement('style');
                            style.innerHTML = [
                                'iframe[src*="dolesdao"]',
                                'iframe[src*="popads"]',
                                'iframe[src*="propeller"]',
                                'iframe[src*="adsterra"]',
                                'iframe[src*="hilltopads"]',
                                'iframe[src*="monetag"]',
                                'div[id*="popunder"]',
                                'div[class*="popunder"]'
                            ].join(',') + '{ display:none!important; }';
                            document.head && document.head.appendChild(style);
                            window.open = function() { return null; };
                        })();
                    """.trimIndent(), null)
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    handler?.proceed()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    blockedCount++
                    return false
                }

                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                    android.util.Log.d("WebConsole", message?.message() ?: "")
                    return true
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
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
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> return super.onKeyDown(keyCode, event)

            KeyEvent.KEYCODE_BACK -> {
                webView.evaluateJavascript("""
                    (function() {
                        var ev = new KeyboardEvent('keydown', {key:'Escape', keyCode:27, bubbles:true});
                        document.dispatchEvent(ev);
                        return (typeof S !== 'undefined' && S.page === 'home') ? 'home' : 'handled';
                    })();
                """.trimIndent()) { result ->
                    val page = result?.replace("\"", "") ?: ""
                    if (page == "home") {
                        runOnUiThread {
                            if (doubleBackToExit) {
                                finish()
                            } else {
                                doubleBackToExit = true
                                val msg = "Nhấn Back lần nữa để thoát" +
                                    if (blockedCount > 0) " • Đã chặn $blockedCount quảng cáo" else ""
                                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                webView.postDelayed({ doubleBackToExit = false }, 2000)
                            }
                        }
                    }
                }
                return true
            }

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
