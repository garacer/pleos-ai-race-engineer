package com.jev.drivecoach

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Radio debrief: five engineer messages built by [Debrief] (unit-tested); the page only draws.
 * Page -> native: only "jev://speak?card=N", "jev://stop" and "jev://back" navigations, intercepted below. No JS bridge, no network.
 */
class HighlightActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var voice: LocalSpeech
    private lateinit var sdk: ReadOnlyDoorProvider
    private lateinit var ux: UxPolicy
    private var pageReady = false
    private var detailsAllowed = false
    private var vehicleText = "차량 SDK 대기"
    private var voiceText = ""
    private var speakCount = 0
    private var lines: List<String> = emptyList()
    /** Card asked for while the previous one is still being spoken; spoken as soon as the engine is free. */
    private var pendingCard: Int? = null

    private fun speakCard(n: Int) {
        val text = lines.getOrNull(n) ?: return
        if (voice.canRequestForTest()) { pendingCard = null; voice.speak("card-${++speakCount}", text) }
        else pendingCard = n
    }
    private fun cancelSpeech() { pendingCard = null; voice.stop() }

    private fun js(call: String) { if (pageReady) web.evaluateJavascript(call, null) }
    private fun q(s: String) = JSONObject.quote(s)
    private fun pushState() {
        js("JEV_DRIVING(${!detailsAllowed});JEV_VEHICLE(${q(vehicleText)});JEV_VOICE(${q(voiceText)});")
    }

    private fun payload(): String {
        val laps = JSONObject(assets.open("jev_laps_demo.json").bufferedReader().use { it.readText() }).getJSONArray("laps").let { a ->
            (0 until a.length()).map { i -> a.getJSONObject(i).run { LapRecord(getInt("lap"), getDouble("seconds"), getDouble("sector1"), getDouble("sector2"), getDouble("sector3")) } }
        }
        val trace = assets.open("lap7_trace.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val corners = JSONObject(trace).getJSONArray("corners").let { a ->
            (0 until a.length()).map { i -> a.getJSONObject(i).run {
                fun d(k: String) = if (isNull(k)) null else getDouble(k)
                CornerSnapshot(getInt("turn"), d("subjectSpeed"), d("referenceSpeed"), d("elapsedDelta"))
            } }
        }
        val b = PitBriefing.analyze(laps)
        val loss = PitBriefing.biggestLoss(corners)
        val cards = Debrief.cards(b, loss, corners)
        lines = cards.map { it.spoken }
        return "JEV_RENDER(${DebriefJson.build(b, loss, cards)},$trace);"
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        web = WebView(this).apply { setBackgroundColor(Color.rgb(10, 15, 22)) }
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        val render = payload()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                web.evaluateJavascript(render, null)
                pushState()
                Log.i("JEV_DASHBOARD", "PITWALL_READY")
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "jev" && u.host == "speak" && detailsAllowed) {
                    u.getQueryParameter("card")?.toIntOrNull()?.let { speakCard(it) }
                } else if (u.scheme == "jev" && u.host == "stop") cancelSpeech()
                else if (u.scheme == "jev" && u.host == "back") { cancelSpeech(); finish() }
                return true
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (request.url.scheme in listOf("http", "https"))
                    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
                    Log.w("JEV_DASHBOARD", "LOCAL_JS_ERROR ${message.lineNumber()} ${message.message()}")
                return true
            }
        }
        voice = LocalSpeech(this) { status ->
            if (speakCount > 0) { voiceText = status; pushState() }
            pendingCard?.let { if (detailsAllowed && voice.canRequestForTest()) speakCard(it) }
        }
        sdk = ReadOnlyDoorProvider(this) { state ->
            vehicleText = if (state.freshness(SystemClock.elapsedRealtime()) == "LIVE") "차량 SDK 연결 · 문 상태 ${state.value}" else "차량 SDK 대기"
            pushState()
        }
        ux = UxPolicy(this) { allowed, _ -> detailsAllowed = allowed; if (!allowed) cancelSpeech(); pushState() }
        setContentView(web)
        val html = assets.open("debrief.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        web.loadDataWithBaseURL("https://jev.invalid/", html, "text/html", "UTF-8", null)
        voice.initialize()
    }
    override fun onStart() { super.onStart(); sdk.connect(); ux.start() }
    override fun onStop() { cancelSpeech(); sdk.disconnect(); ux.stop(); super.onStop() }
    override fun onDestroy() { voice.close(); pageReady = false; web.stopLoading(); web.destroy(); super.onDestroy() }
}
