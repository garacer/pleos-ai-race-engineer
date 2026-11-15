package com.jev.drivecoach

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Offline packaged dashboard; the surrounding Pleos shell belongs to the emulator. */
class DashboardActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var voice: LocalSpeech
    private lateinit var sdk: ReadOnlyDoorProvider
    private lateinit var ux: UxPolicy
    private lateinit var voiceStatus: TextView
    private lateinit var prepareButton: Button
    private lateinit var speakButton: Button
    private lateinit var sessionButton: Button
    private lateinit var highlightButton: Button
    private lateinit var probeButton: Button
    private val developmentTools get() = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val manual = ManualVoiceTestGate()
    private var pageReady = false
    private var detailsAllowed = false
    private var doorText = "도어 SDK 대기 · 실차 미검증"

    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
    private fun pushState() {
        if (!pageReady) return
        val state = JSONObject().put("detailsAllowed", detailsAllowed).put("door", doorText)
        web.evaluateJavascript("window.JEV_NATIVE_UPDATE && window.JEV_NATIVE_UPDATE($state);", null)
    }
    private fun updateButtons() {
        probeButton.isEnabled = developmentTools && detailsAllowed
        sessionButton.isEnabled = detailsAllowed
        prepareButton.isEnabled = developmentTools && detailsAllowed && !manual.initializationRequested
        speakButton.isEnabled = developmentTools && detailsAllowed && voice.canRequestForTest() && !manual.utteranceRequested
    }
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 19, 30))
        }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.setPadding(dp(12), dp(10), dp(12), 0)
        root.addView(bar, LinearLayout.LayoutParams(-1, dp(90)))
        fun control(label: String, primary: Boolean = false) = Button(this).apply {
            text = label; textSize = 22f; isAllCaps = false; stateListAnimator = null
            minHeight = 0; minimumHeight = 0
            setTextColor(if (primary) Color.rgb(14, 30, 10) else Color.rgb(229, 239, 246))
            if (primary) setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (primary) Color.rgb(169, 229, 121) else Color.rgb(27, 48, 65))
                cornerRadius = dp(14).toFloat(); setStroke(dp(1), if (primary) Color.rgb(169, 229, 121) else Color.rgb(41, 66, 86))
            }
            bar.addView(this, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(12) })
        }
        highlightButton = control("▶ 하이라이트 보기 · 엔지니어 브리핑", true)
        highlightButton.setOnClickListener { startActivity(Intent(this, HighlightActivity::class.java)) }
        sessionButton = control("9랩 기록 · 세션 요약")
        prepareButton = control("음성 엔진 준비 확인")
        speakButton = control("한국어 발화 1회 시험")
        probeButton = control("TTS API 진단")
        probeButton.setOnClickListener { if(developmentTools && detailsAllowed) startActivity(Intent(this, TtsProbeActivity::class.java)) }
        for (tool in listOf(prepareButton, speakButton, probeButton))
            tool.visibility = if (developmentTools) View.VISIBLE else View.GONE
        voiceStatus = TextView(this).apply {
            text = "자막 기본 · 동적 한국어 미시험 · SDK 준비와 발화 완료는 별도"
            textSize = 14f; setTextColor(Color.rgb(165, 190, 209))
            setPadding(dp(8), dp(2), dp(8), dp(2)); maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        root.addView(voiceStatus, LinearLayout.LayoutParams(-1, dp(40)))
        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            useWideViewPort = true
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true; pushState()
                Log.i("JEV_DASHBOARD", "LOCAL_PAGE_READY app=com.jev.drivecoach.dashboard")
                web.postDelayed({
                    if (pageReady && !isFinishing && !isDestroyed) web.evaluateJavascript("""
                        (function(){const box=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,scroll:e.scrollHeight,client:e.clientHeight}};return JSON.stringify({viewport:[innerWidth,innerHeight,devicePixelRatio],font:getComputedStyle(document.body).fontSize,tyres:[...document.querySelectorAll('.wheelval')].map(box),driver:[...document.querySelector('.driver').children].map(box),side:[...document.querySelector('.side').children].map(box)});})()
                    """.trimIndent()) { value -> Log.i("JEV_LAYOUT", value) }
                }, 3500)

            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return if (request.url.scheme in listOf("http", "https"))
                    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
                    Log.w("JEV_DASHBOARD", "LOCAL_JS_ERROR ${message.lineNumber()} ${message.message()}")
                return true
            }
        }
        // No JavaScript-to-native bridge, external web URL or additional network permission.
        voice = LocalSpeech(this) { status ->
            voiceStatus.text = status
            updateButtons()
        }
        sdk = ReadOnlyDoorProvider(this) { state ->
            val freshness = state.freshness(SystemClock.elapsedRealtime())
            doorText = if (freshness == "LIVE") "도어 ${state.value} · SDK 값 / 실차 미검증"
                       else "도어 $freshness · 현재값 없음"
            pushState()
        }
        ux = UxPolicy(this) { allowed, _ ->
            detailsAllowed = allowed; updateButtons(); pushState()
        }
        sessionButton.setOnClickListener {
            if (detailsAllowed) startActivity(Intent(this, MainActivity::class.java))
        }
        prepareButton.setOnClickListener {
            if (developmentTools && manual.beginInitialization(detailsAllowed)) {
                voice.initialize(); updateButtons()
            }
        }
        speakButton.setOnClickListener {
            if (developmentTools && manual.requestUtterance(voice.canRequestForTest(), detailsAllowed)) {
                voice.speak("dashboard-ko-once", "기록 재생 음성 시험입니다.")
                updateButtons()
            }
        }
        setContentView(root)
        updateButtons()
        val html = assets.open("dashboard.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        web.loadDataWithBaseURL("https://jev.invalid/", html, "text/html", "UTF-8", null)
    }
    override fun onStart() { super.onStart(); sdk.connect(); ux.start() }
    override fun onStop() {
        voice.stop(); sdk.disconnect(); ux.stop()
        if (pageReady) web.evaluateJavascript("window.JEV_BACKGROUND && window.JEV_BACKGROUND();", null)
        super.onStop()
    }
    override fun onDestroy() {
        voice.close(); pageReady = false; web.stopLoading(); web.destroy()
        super.onDestroy()
    }
}
