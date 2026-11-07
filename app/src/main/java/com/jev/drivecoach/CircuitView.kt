package com.jev.drivecoach
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
class CircuitView(context:Context):View(context){
 var points:List<GpsFix> = emptyList()
 var cursorIndex=0
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 override fun onDraw(c:Canvas){
  super.onDraw(c)
  val w=width.toFloat();val h=height.toFloat();paint.strokeWidth=1f;paint.color=Color.rgb(31,52,69)
  for(i in 1..9){c.drawLine(w*i/10,0f,w*i/10,h,paint);c.drawLine(0f,h*i/10,w,h*i/10,paint)}
  val current=points.getOrNull(cursorIndex)
  val status=CircuitData.status(current,SystemClock.elapsedRealtime())
  val plot=CircuitData.project(points)
  val fit=if(plot.isEmpty())1f else minOf((w-80).coerceAtLeast(1f)/(plot.maxOf{it.x}-plot.minOf{it.x}).coerceAtLeast(0.000001).toFloat(),(h-80).coerceAtLeast(1f)/(plot.maxOf{it.y}-plot.minOf{it.y}).coerceAtLeast(0.000001).toFloat())
  fun x(p:MapPoint)=w/2+(p.x.toFloat()-.5f)*fit
  fun y(p:MapPoint)=h/2+(p.y.toFloat()-.5f)*fit
  paint.color=Color.rgb(79,212,192);paint.strokeWidth=7f;paint.style=Paint.Style.STROKE
  if(plot.size>1){val p=Path();plot.forEachIndexed{i,v->if(i==0)p.moveTo(x(v),y(v))else p.lineTo(x(v),y(v))};c.drawPath(p,paint)}
  paint.style=Paint.Style.FILL;paint.textSize=28f;paint.color=Color.rgb(221,235,244)
  val text=if(plot.isEmpty())"GPS 미연결 · 서킷 경로/현재 위치 없음"else"GPS 기록 경로 · $status"
  c.drawText(text,20f,h-24f,paint);contentDescription=text
  if(status in listOf("LIVE","REPLAY")&&plot.getOrNull(cursorIndex)!=null){
   val p=plot[cursorIndex];val x=x(p);val y=y(p);c.drawCircle(x,y,9f,paint)
   CircuitData.direction(current)?.let{c.save();c.rotate(it.toFloat(),x,y);val a=Path();a.moveTo(x,y-25);a.lineTo(x-10,y-8);a.lineTo(x+10,y-8);a.close();c.drawPath(a,paint);c.restore()}
  }
 }
}
