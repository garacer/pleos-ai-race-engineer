package com.jev.drivecoach

import kotlin.math.cos

enum class PositionSource { LIVE, REPLAY }
data class GpsFix(val latitude:Double, val longitude:Double, val bearingDegrees:Double?=null,
    val receivedElapsedMs:Long?=null, val source:PositionSource=PositionSource.LIVE)
data class MapPoint(val x:Double,val y:Double)
object CircuitData {
    fun valid(f:GpsFix)=f.latitude.isFinite() && f.longitude.isFinite() && f.latitude in -90.0..90.0 && f.longitude in -180.0..180.0
    fun status(f:GpsFix?,now:Long):String = when {
        f==null -> "DISCONNECTED"
        !valid(f) -> "INVALID"
        f.source==PositionSource.REPLAY -> "REPLAY"
        f.receivedElapsedMs==null -> "TIME_UNKNOWN"
        f.receivedElapsedMs<0 || f.receivedElapsedMs>now -> "INVALID_TIME"
        now-f.receivedElapsedMs>5000 -> "STALE"
        else -> "LIVE"
    }
    fun direction(f:GpsFix?):Double?=f?.bearingDegrees?.takeIf { it.isFinite() && it>=0 && it<360 }
    /** Recorded point trace only. No inferred track boundary or inferred heading/yaw. */
    fun project(points:List<GpsFix>):List<MapPoint> {
        val usable=points.filter { valid(it) };if(usable.isEmpty())return emptyList()
        val latitude=usable.map { it.latitude }.average()
        val scale=cos(Math.toRadians(latitude)).coerceAtLeast(0.00001)
        val origin=usable.first().longitude
        val xy=usable.map {
            val dx=((it.longitude-origin+540)%360)-180
            MapPoint(dx*scale,-it.latitude)
        }
        val minX=xy.minOf { it.x };val maxX=xy.maxOf { it.x }
        val minY=xy.minOf { it.y };val maxY=xy.maxOf { it.y }
        val span=maxOf(maxX-minX,maxY-minY)
        if(span==0.0)return xy.map { MapPoint(0.5,0.5) }
        return xy.map { MapPoint(0.5+(it.x-(maxX+minX)/2)/span*0.8,0.5+(it.y-(maxY+minY)/2)/span*0.8) }
    }
}
