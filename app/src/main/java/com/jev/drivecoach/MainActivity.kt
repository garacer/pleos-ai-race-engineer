package com.jev.drivecoach

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
        val bgColor=Color.rgb(8,19,30); val cardColor=Color.rgb(17,35,51); val line=Color.rgb(41,66,86)
        val ink=Color.rgb(229,239,246); val muted=Color.rgb(143,169,189); val lime=Color.rgb(169,229,121); val chipColor=Color.rgb(27,48,65)
        fun dp(n:Int)=(resources.displayMetrics.density*n).toInt()
        fun shape(fill:Int,stroke:Int,r:Int)=GradientDrawable().apply { setColor(fill);cornerRadius=dp(r).toFloat();setStroke(dp(1),stroke) }
        fun vbox()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        fun hbox(parent:LinearLayout)=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12) }) }
        fun label(parent:LinearLayout,text:String,size:Float,color:Int=ink,bold:Boolean=false)=TextView(this).apply {
            this.text=text;textSize=size*1.6f;setTextColor(color);if(bold)setTypeface(typeface,Typeface.BOLD);setPadding(0,dp(2),0,dp(2));parent.addView(this) }
        fun card(parent:LinearLayout,title:String)=vbox().apply {
            background=shape(cardColor,line,14);setPadding(dp(24),dp(18),dp(24),dp(22))
            parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
            label(this,title,13f,muted,true) }
        fun chip(parent:LinearLayout,text:String,primary:Boolean=false,action:()->Unit)=Button(this).apply {
            this.text=text;isAllCaps=false;textSize=22f;stateListAnimator=null;minHeight=0;minimumHeight=0;setPadding(dp(8),0,dp(8),0)
            setTextColor(if(primary)Color.rgb(14,30,10) else ink);if(primary)setTypeface(typeface,Typeface.BOLD)
            background=shape(if(primary)lime else chipColor,if(primary)lime else line,10)
            setOnClickListener { action() }
            parent.addView(this,LinearLayout.LayoutParams(0,dp(76),1f).apply { marginEnd=dp(12) }) }

        val base=vbox().apply { setPadding(dp(24),dp(18),dp(24),dp(18));setBackgroundColor(bgColor) }
        val head=hbox(base)
        Button(this).apply {
            text="‹ 대시보드";isAllCaps=false;textSize=22f;setTextColor(ink);stateListAnimator=null;minHeight=0;minimumHeight=0;setPadding(dp(16),0,dp(16),0)
            background=shape(bgColor,line,24);setOnClickListener { finish() }
            head.addView(this,LinearLayout.LayoutParams(-2,dp(72)).apply { marginEnd=dp(20) }) }
        val titles=vbox();head.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        label(titles,"9랩 기록 · 세션 요약",22f,ink,true)
        label(titles,"2026-10-04 오후 9랩 · 기록 재생 · 실시간 랩/섹터 아님",13f,muted)
        uxLabel=label(titles,"AAOS UX 미확인 · 단순 화면",12f,muted)

        val cols=hbox(base)
        val left=vbox();cols.addView(left,LinearLayout.LayoutParams(0,-2,3f).apply { marginEnd=dp(12) })
        val right=vbox();cols.addView(right,LinearLayout.LayoutParams(0,-2,2f))

        // left: current lap + transport + comparison
        val lapCard=card(left,"현재 랩 · 기록 재생")
        demoLabel=label(lapCard,"시연 준비 · 기록 재생 → 랩 비교 → 세션 요약",13f,lime)
        summary=label(lapCard,"",34f,ink,true);coach=label(lapCard,"",18f)
        playback=label(lapCard,"",12f,muted)
        val transport=hbox(lapCard)
        val restart=chip(transport,"⏮ 처음") { demoStage="REPLAY";speech.stop();sample.stop();session.restart();showLap(true);scheduleTick() };detailedViews.add(restart)
        val previous=chip(transport,"◀ 이전 랩") { session.previous();showLap(true);scheduleTick() };detailedViews.add(previous)
        chip(transport,"▶ 재생",true) { demoStage="REPLAY";sample.stop();session.play();showLap(true);scheduleTick() }
        chip(transport,"⏸ 일시중지") { session.pause();handler.removeCallbacks(tick);speech.stop();sample.stop();showLap(false) }
        chip(transport,"⏹ 중지") { session.stop();handler.removeCallbacks(tick);speech.stop();sample.stop();showLap(false) }
        chip(transport,"다음 랩 ▶") { session.next();showLap(true);scheduleTick() }
        val compareCard=card(left,"섹터 · 베스트 비교");detailedViews.add(compareCard)
        detail=label(compareCard,"",15f)
        val compareRow=hbox(compareCard)
        chip(compareRow,"이전 베스트와 비교") { compareReference=CompareReference.PRIOR_BEST;showLap(false) }
        chip(compareRow,"세션 베스트와 비교") { compareReference=CompareReference.SESSION_BEST;showLap(false) }
        comparison=label(compareCard,"",16f)
        sessionPanel=label(left,"",16f)

        // right: demo steps, voice, SDK
        val demoCard=card(right,"시연 순서");detailedViews.add(demoCard)
        val demoRow1=hbox(demoCard);val demoRow2=hbox(demoCard)
        chip(demoRow1,"시연 시작 · 자막",true) { dynamicVoice=false;demoStage="REPLAY";sample.stop();speech.stop();compareReference=CompareReference.PRIOR_BEST;session.restart();showLap(false);scheduleTick() }
        chip(demoRow1,"랩 비교") { pauseReview();demoStage="COMPARE";showLap(false) }
        chip(demoRow2,"세션 요약") { pauseReview();demoStage="SUMMARY";showLap(false) }
        chip(demoRow2,"현재 랩으로") { demoStage="COMPARE";showLap(false) }
        val voiceCard=card(right,"음성")
        voiceLabel=label(voiceCard,"로컬 한국어 음성 확인 중",12f,muted)
        val voiceRow=hbox(voiceCard)
        val voiceToggle=chip(voiceRow,"동적 음성 켜기/끄기") { dynamicVoice=!dynamicVoice;if(!dynamicVoice)speech.stop();showLap(false) };detailedViews.add(voiceToggle)
        val sampleStatus=label(voiceCard,"과거 예시 음성 · 현재 랩 안내 아님",12f,muted);detailedViews.add(sampleStatus)
        sample=LocalSample(this) { message -> runOnUiThread { sampleStatus.text=message } }
        val sampleRow=hbox(voiceCard);detailedViews.add(sampleRow)
        chip(sampleRow,"과거 예시 음성 재생") { speech.stop();sample.play() }
        chip(sampleRow,"예시 중지") { sample.stop() }
        val sdkCard=card(right,"차량 SDK (읽기 전용)");detailedViews.add(sdkCard)
        sdkLabel=label(sdkCard,"SDK 조회 연결 중",12f,muted)
        val sdkRow=hbox(sdkCard)
        chip(sdkRow,"다시 연결") { sdk.connect() }
        chip(sdkRow,"연결 해제") { sdk.disconnect() }
        sdk=ReadOnlyDoorProvider(this) { r -> sdkLabel.text=r.display(SystemClock.elapsedRealtime()) }
        speech=LocalSpeech(this) { s -> runOnUiThread { voiceLabel.text=s } };speech.initialize()
        ux=UxPolicy(this) { allowed,s -> runOnUiThread { detailed=allowed;uxLabel.text=s;renderDetailMode() } }
        val scroll=ScrollView(this).apply { setBackgroundColor(Color.rgb(8,19,30));isFillViewport=true };scroll.addView(base);setContentView(scroll)
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
