package com.jev.drivecoach
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
class LocalSample(private val context:Context,private val changed:(String)->Unit) {
    private var player:MediaPlayer?=null
    private var starts=0
    fun play() {
        if(player?.isPlaying==true) { Log.i("JEV_AUDIO","DUPLICATE_SUPPRESSED");return }
        stop()
        try {
            val p=MediaPlayer();player=p
            context.assets.openFd("past_coaching_example.mp3").use { p.setDataSource(it.fileDescriptor,it.startOffset,it.length) }
            p.setOnCompletionListener { changed("과거 예시 음성 완료 · 현재 랩 안내 아님");Log.i("JEV_AUDIO","COMPLETED starts=$starts");stop(false) }
            p.setOnErrorListener { _,what,extra -> changed("예시 음성 재생 실패");Log.e("JEV_AUDIO","ERROR $what/$extra");stop(false);true }
            p.prepare();starts++;p.start()
            changed("과거 예시 음성 재생 중 · 현재 랩 안내 아님")
            Log.i("JEV_AUDIO","START durationMs=${p.duration} starts=$starts")
        } catch(e:Exception) { changed("로컬 예시 음성 열기 실패");Log.w("JEV_AUDIO",e.javaClass.simpleName);stop(false) }
    }
    fun stop(show:Boolean=true) { player?.release();player=null;if(show)changed("과거 예시 음성 중지 · 현재 랩 안내 아님");Log.i("JEV_AUDIO","STOP") }
}
