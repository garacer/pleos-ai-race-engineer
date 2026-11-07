package com.jev.drivecoach

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** One explicit SDK-contract probe. Never used for automatic coaching. */
class TtsProbeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private lateinit var status: TextView
    private lateinit var prepare: Button
    private lateinit var request: Button
    private lateinit var ux: UxPolicy
    private var allowed = false
    private var closed = false
    private var initRequested = false
    private var apiInitialized = false
    private var readyEvents = 0
    private var requested = 0
    private var started = 0
    private var completed = 0
    private var terminal = false
    private var error = ""
    private fun buttons() {
        prepare.isEnabled = allowed && !initRequested && !terminal
        request.isEnabled = allowed && apiInitialized && requested == 0 && !terminal
    }
    private fun record(event: String) {
        val s = JSONObject().put("event",event).put("mode","ON_DEVICE").put("locale","ko-KR")
            .put("diagnostic_only",true).put("api_initialized",apiInitialized).put("ready_events",readyEvents)
            .put("requested",requested).put("started",started).put("completed",completed)
            .put("error",error).put("utc_ms",System.currentTimeMillis()).put("physical_audibility","not_observed")
        File(filesDir,"pleos-tts-api-probe.json").writeText(s.toString(2))
        Log.i("JEV_TTS_PROBE",s.toString())
        status.text="진단 전용 · API=$apiInitialized · ready=$readyEvents · 요청=$requested · 시작=$started · 완료=$completed\n$event $error\n실제 청취 여부는 별도 · 자동 재시도 없음"
        buttons()
    }
    private fun event(block:()->Unit) { handler.post { if(!closed) block() } }
    private val initTimeout=Runnable { if(!apiInitialized && !terminal){terminal=true;error="API_INIT_TIMEOUT";record("TIMEOUT")} }
    private val requestTimeout=Runnable { if(requested>0 && completed==0 && !terminal){terminal=true;error="REQUEST_TIMEOUT";record("TIMEOUT");try{tts?.stop()}catch(_:Exception){}} }
    private val listener=object:EventListener {
        override fun onReady()=event { readyEvents++;record("READY_EVENT") }
        override fun onStart()=event { if(requested>0 && !terminal){started++;record("START_EVENT")} }
        override fun onUpdatedRms(rms:Double)=Unit
        override fun onDone()=event { if(requested>0 && !terminal){completed++;terminal=true;handler.removeCallbacks(requestTimeout);record("DONE_EVENT")} }
        override fun onStop()=event { if(requested>0 && !terminal){terminal=true;handler.removeCallbacks(requestTimeout);record("STOP_EVENT")} }
        override fun onError(e:ErrorType)=event { if(!terminal){terminal=true;error=e.toString();handler.removeCallbacksAndMessages(null);record("ERROR_EVENT")} }
    }
    override fun onCreate(saved:Bundle?) {
        super.onCreate(saved)
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) { finish(); return }
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24)}
        root.addView(TextView(this).apply{text="TTS SDK 계약 진단 · 한국어 고정 문장 1회\nAPI 초기화 성공을 엔진 준비·발화 성공으로 표시하지 않습니다.";textSize=22f})
        prepare=Button(this).apply{text="진단 API 초기화 1회"};root.addView(prepare)
        request=Button(this).apply{text="진단 한국어 요청 1회"};root.addView(request)
        status=TextView(this).apply{textSize=20f};root.addView(status)
        ux=UxPolicy(this){value,_->allowed=value;buttons()}
        prepare.setOnClickListener {
            if(allowed && !initRequested && !terminal){
                initRequested=true;record("INITIALIZING");handler.postDelayed(initTimeout,8000)
                try{
                    tts=TextToSpeech(applicationContext,Mode.ON_DEVICE)
                    tts!!.initialize(object:OnInitializeListener{
                        override fun onSuccess()=event {
                            if(!terminal){try{tts!!.setLocale(Locale.KOREA);apiInitialized=true;handler.removeCallbacks(initTimeout);record("API_INITIALIZED")}catch(e:Exception){terminal=true;error=e.javaClass.simpleName;record("CONFIG_EXCEPTION")}}
                        }
                        override fun onFailure(e:ErrorType)=event {if(!terminal){terminal=true;error=e.toString();handler.removeCallbacks(initTimeout);record("API_FAILURE")}}
                    })
                    tts!!.addEventListener(listener)
                }catch(e:Exception){terminal=true;error=e.javaClass.simpleName;handler.removeCallbacks(initTimeout);record("INIT_EXCEPTION")}
            }
        }
        request.setOnClickListener {
            if(allowed && apiInitialized && requested==0 && !terminal){
                requested=1;record("REQUEST_WITH_API_READY_ONLY");handler.postDelayed(requestTimeout,14000)
                try{tts!!.speak("기록 재생 음성 시험입니다.")}catch(e:Exception){terminal=true;error=e.javaClass.simpleName;handler.removeCallbacks(requestTimeout);record("REQUEST_EXCEPTION")}
            }
        }
        setContentView(root);record("CREATED")
    }
    override fun onStart(){super.onStart();ux.start()}
    override fun onStop(){ux.stop();if(requested>0 && !terminal){terminal=true;handler.removeCallbacks(requestTimeout);record("BACKGROUND_STOP");try{tts?.stop()}catch(_:Exception){}};super.onStop()}
    override fun onDestroy(){closed=true;handler.removeCallbacksAndMessages(null);try{tts?.removeEventListener(listener);tts?.release()}catch(_:Exception){};super.onDestroy()}
}
