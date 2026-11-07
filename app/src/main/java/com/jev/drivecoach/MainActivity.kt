package com.jev.drivecoach

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

class MainActivity: Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var session: ReplaySession
    private lateinit var laps: List<LapRecord>
    private lateinit var speech: LocalSpeech
    private lateinit var sdk: ReadOnlyDoorProvider
    private lateinit var sample: LocalSample
    private lateinit var ux: UxPolicy
    private lateinit var summary: TextView
    private lateinit var coach: TextView
    private lateinit var detail: TextView
    private lateinit var playback: TextView
    private lateinit var uxLabel: TextView
    private lateinit var voiceLabel: TextView
    private lateinit var sdkLabel: TextView
    private val detailedViews=mutableListOf<View>()
    private var detailed=false
    private var compareReference=CompareReference.PRIOR_BEST
    private lateinit var comparison:TextView
    private lateinit var demoLabel:TextView
    private lateinit var sessionPanel:TextView
    private var demoStage="READY"
    private var dynamicVoice=false

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val array=JSONObject(assets.open("jev_laps_demo.json").bufferedReader().use{it.readText()}).getJSONArray("laps")
        laps=(0 until array.length()).map { i -> val l=array.getJSONObject(i);LapRecord(l.getInt("lap"),l.getDouble("seconds"),l.getDouble("sector1"),l.getDouble("sector2"),l.getDouble("sector3")) }
        session=ReplaySession(laps.size)
        val base=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(28,20,28,20);setBackgroundColor(Color.rgb(10,18,29)) }
        fun label(text:String,size:Float=18f)=TextView(this).apply { this.text=text;textSize=size;setTextColor(Color.WHITE);setPadding(0,6,0,6);base.addView(this) }
        fun button(text:String,action:()->Unit)=Button(this).apply { this.text=text;setOnClickListener { action() };base.addView(this) }
        label("플레오스 AI 레이스 엔지니어 | 기록 재생 코치",25f)
        label("2026-10-04 오후 9랩 · REPLAY · LIVE 랩/섹터 아님",15f)
        uxLabel=label("AAOS UX 미확인 · 단순 화면",14f)
        
        demoLabel=label("시연 준비 · 기록 재생 → 랩 비교 → 세션 요약",18f)
        val demoStart=button("시연 시작 · 자막") { dynamicVoice=false;demoStage="REPLAY";sample.stop();speech.stop();compareReference=CompareReference.PRIOR_BEST;session.restart();showLap(false);scheduleTick() };detailedViews.add(demoStart)
        val demoCompare=button("랩 비교 보기") { pauseReview();demoStage="COMPARE";showLap(false) };detailedViews.add(demoCompare)
        val demoSummary=button("세션 요약 보기") { pauseReview();demoStage="SUMMARY";showLap(false) };detailedViews.add(demoSummary)
        val demoReturn=button("현재 랩으로 돌아가기") { demoStage="COMPARE";showLap(false) };detailedViews.add(demoReturn)
        val voiceToggle=button("동적 음성 선택 · 기본 꺼짐") { dynamicVoice=!dynamicVoice;if(!dynamicVoice)speech.stop();showLap(false) };detailedViews.add(voiceToggle)
        sessionPanel=label("",18f)
        summary=label("",30f);coach=label("",22f)
        playback=label("",14f)
        button("재생 시작") { demoStage="REPLAY";sample.stop();session.play();showLap(true);scheduleTick() }
        button("일시중지") { session.pause();handler.removeCallbacks(tick);speech.stop();sample.stop();showLap(false) }
        button("재생 중지") { session.stop();handler.removeCallbacks(tick);speech.stop();sample.stop();showLap(false) }
        val restart=button("처음부터 재시작") { demoStage="REPLAY";speech.stop();sample.stop();session.restart();showLap(true);scheduleTick() };detailedViews.add(restart)
        button("다음 랩") { session.next();showLap(true);scheduleTick() }
        val previous=button("이전 랩") { session.previous();showLap(true);scheduleTick() };detailedViews.add(previous)
        voiceLabel=label("로컬 한국어 음성 확인 중",14f)
        val sampleStatus=label("과거 예시 음성 · 현재 랩 안내 아님",14f);detailedViews.add(sampleStatus)
        sample=LocalSample(this) { message -> runOnUiThread { sampleStatus.text=message } }
        val samplePlay=button("과거 예시 음성 재생") { speech.stop();sample.play() };detailedViews.add(samplePlay)
        val sampleStop=button("예시 음성 중지") { sample.stop() };detailedViews.add(sampleStop)
        detail=label("",17f);detailedViews.add(detail)
        val priorCompare=button("이전 베스트 비교") { compareReference=CompareReference.PRIOR_BEST;showLap(false) };detailedViews.add(priorCompare)
        val sessionCompare=button("세션 베스트 비교") { compareReference=CompareReference.SESSION_BEST;showLap(false) };detailedViews.add(sessionCompare)
        comparison=label("",18f);detailedViews.add(comparison)
        sdkLabel=label("SDK 조회 연결 중",13f);detailedViews.add(sdkLabel)
        val connect=button("SDK 다시 연결") { sdk.connect() };detailedViews.add(connect)
        val disconnect=button("SDK 연결 해제") { sdk.disconnect() };detailedViews.add(disconnect)
        sdk=ReadOnlyDoorProvider(this) { r -> sdkLabel.text=r.display(SystemClock.elapsedRealtime()) }
        speech=LocalSpeech(this) { s -> runOnUiThread { voiceLabel.text=s } };speech.initialize()
        ux=UxPolicy(this) { allowed,s -> runOnUiThread { detailed=allowed;uxLabel.text=s;renderDetailMode() } }
        val scroll=ScrollView(this);scroll.addView(base);setContentView(scroll)
        showLap(false);renderDetailMode()
    }
    private fun renderDetailMode() {
        if(!detailed && ::sample.isInitialized) sample.stop()
        for(v in detailedViews) v.visibility=if(detailed) View.VISIBLE else View.GONE
        if(::sessionPanel.isInitialized) sessionPanel.visibility=if(detailed && demoStage=="SUMMARY")View.VISIBLE else View.GONE
    }
    private fun pauseReview() { session.pause();handler.removeCallbacks(tick);speech.stop();sample.stop() }
    private fun showLap(voice:Boolean) {
        val lap=laps[session.index];val result=ReplayCoach.describe(laps,session.index)
        summary.text="LAP ${lap.number}  ${ReplayCoach.time(lap.seconds)}"
        coach.text=result.shortText
        detail.text=result.detail
        comparison.text=LapReview.text(laps,session.index,compareReference)
        sessionPanel.text=SessionReview.text(laps,compareReference)
        demoLabel.text=when(demoStage) {
            "REPLAY" -> "시연 1/3 · 기록 리플레이 · 자막 코칭"
            "COMPARE" -> "시연 2/3 · 랩 비교 · 선택 기준 유지"
            "SUMMARY" -> "시연 3/3 · 세션 요약 · 전체 기록 보존"
            else -> "시연 준비 · 기록 재생 → 랩 비교 → 세션 요약"
        }+" · 동적 음성 "+(if(dynamicVoice)"선택됨 · 완료/오류는 음성 상태 참조"else"꺼짐")
        renderDetailMode()
        playback.text="재생: ${session.state} · 검토용 10초/랩 (실제 랩 경과시간 아님)"
        if(dynamicVoice && voice && session.state=="PLAYING") speech.speak("${session.runId}-${lap.number}",result.voiceText)
    }
    private fun scheduleTick() { handler.removeCallbacks(tick);if(session.state=="PLAYING") handler.postDelayed(tick,10000) }
    private val tick=object:Runnable {
        override fun run() {
            if(session.state=="PLAYING") { session.tick();if(session.state=="COMPLETE")demoStage="SUMMARY";showLap(true) }
            scheduleTick()
        }
    }
    override fun onStart() { super.onStart();sdk.connect();ux.start() }
    override fun onStop() { handler.removeCallbacksAndMessages(null);session.pause();speech.stop();sample.stop();sdk.disconnect();ux.stop();super.onStop() }
    override fun onDestroy() { speech.close();super.onDestroy() }
}
