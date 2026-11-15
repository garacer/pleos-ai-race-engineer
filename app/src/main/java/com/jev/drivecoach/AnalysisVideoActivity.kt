package com.jev.drivecoach

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView

/** AI 엔지니어 최종 분석: 4랩 대 7랩 분석 영상(앱 안에 포함, 네트워크 없음). 주행 중 표시 대상 아님. */
class AnalysisVideoActivity : Activity() {
    private lateinit var video: VideoView
    private var position = 0

    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val bg = Color.rgb(8, 19, 30); val ink = Color.rgb(229, 239, 246); val muted = Color.rgb(143, 169, 189); val line = Color.rgb(41, 66, 86)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(24), dp(16), dp(24), dp(16)) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        root.addView(head, LinearLayout.LayoutParams(-1, -2))
        Button(this).apply {
            text = "‹ 대시보드"; isAllCaps = false; textSize = 22f; setTextColor(ink); stateListAnimator = null
            minHeight = 0; minimumHeight = 0; setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(24).toFloat(); setStroke(dp(1), line) }
            setOnClickListener { finish() }
            head.addView(this, LinearLayout.LayoutParams(-2, dp(72)).apply { marginEnd = dp(20) })
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        head.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        titles.addView(TextView(this).apply { text = "AI 엔지니어 최종 분석 · 4랩 대 7랩"; textSize = 34f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD) })
        titles.addView(TextView(this).apply {
            text = "2026-10-04 영암 주행 기록과 온보드 영상으로 AI 레이스 엔지니어가 만든 턴별 분석 · 노면 화살표는 GPS 기록 투영 예시 · 실시간 안내 아님"
            textSize = 18f; setTextColor(muted)
        })
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        video = VideoView(this)
        frame.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        val controls = MediaController(this); controls.setAnchorView(video); video.setMediaController(controls)
        video.setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.ai_final_analysis}"))
        video.setOnPreparedListener { video.seekTo(position); video.start() }
        setContentView(root)
    }
    override fun onStop() { if (::video.isInitialized) { position = video.currentPosition; video.pause() }; super.onStop() }
    override fun onStart() { super.onStart(); if (::video.isInitialized && position > 0) { video.seekTo(position); video.start() } }
}
