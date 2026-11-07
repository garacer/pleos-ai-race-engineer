package com.jev.drivecoach
import java.util.Locale
import kotlin.math.abs

data class SessionStats(val count:Int,val best:LapRecord?,val total:Double?,val mean:Double?,val median:Double?,val invalidCount:Int)
object SessionReview {
 fun stats(laps:List<LapRecord>):SessionStats {
  val invalid=laps.count { l -> !listOf(l.seconds,l.s1,l.s2,l.s3).all { it.isFinite() && it>0 } || abs(l.seconds-l.s1-l.s2-l.s3)>0.15 }
  if(laps.isEmpty() || invalid>0)return SessionStats(laps.size,null,null,null,null,invalid)
  val sorted=laps.map { it.seconds }.sorted();val n=sorted.size
  val median=if(n%2==1)sorted[n/2]else(sorted[n/2-1]+sorted[n/2])/2
  return SessionStats(n,laps.minByOrNull { it.seconds },sorted.sum(),sorted.average(),median,0)
 }
 fun text(laps:List<LapRecord>,mode:CompareReference):String {
  val s=stats(laps)
  val reference=if(mode==CompareReference.PRIOR_BEST)"이전 베스트 · 각 랩 이전 기록만"else"세션 베스트 · 전체 기록 사후 비교"
  if(s.count==0)return "세션 요약 · 기록 없음"
  if(s.invalidCount>0)return "세션 요약 · 전체 ${s.count}랩 중 ${s.invalidCount}개 유효성 확인 필요\n기록을 자동 제거하지 않았습니다. 집계·순위 계산 보류."
  val b=s.best!!
  val lines=laps.mapIndexed { i,l ->
   val c=LapReview.compare(laps,i,mode)
   val delta=c.totalDelta?.let { String.format(Locale.US,"%+.1f초",if(abs(it)<0.05)0.0 else it) }?:"기준 없음"
   "${l.number}랩 ${ReplayCoach.time(l.seconds)} | ${c.reference?.let { "${it.number}랩 대비 " }?:""}$delta"+(if(l.number==9)" · S3 원인 미확정"else"")
  }.joinToString("\n")
  return "세션 요약 · 전체 ${s.count}랩 보존\n베스트 ${b.number}랩 ${ReplayCoach.time(b.seconds)}\n전체 기록 합계 ${ReplayCoach.time(s.total!!)} · 평균 ${ReplayCoach.time(s.mean!!)} · 중앙값 ${ReplayCoach.time(s.median!!)}\n평균·합계에 9랩 3:23.6도 포함 · 실제 세션 벽시계 시간이 아님\n비교 기준: $reference\n$lines\n9랩 S3 137.7초: 원인 미확정. 임의 제외·주행 원인 추정 없음.\n출처: 2026-10-04 오후 기록 REPLAY · 라이브 차량 랩/센서 아님\n음성: 시연은 자막 우선. 동적 한국어 TTS 완료/오류는 현재 음성 상태에서 확인. 실제 청취는 별도 확인. 과거 예시 MP3는 현재 랩 음성이 아님."
 }
}
