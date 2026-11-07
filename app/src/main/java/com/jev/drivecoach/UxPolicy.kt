package com.jev.drivecoach

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/** AAOS UX restriction signal; not a Pleos Vehicle SDK speed/gear signal. */
class UxPolicy(private val context: Context, private val changed: (Boolean, String) -> Unit) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { apply(it) }
    private fun apply(r: CarUxRestrictions?) {
        val allowed = r?.let { detailsAllowed(it.isRequiresDistractionOptimization, it.activeRestrictions) } ?: false
        changed(allowed, when { r==null -> "AAOS UX 미확인 · 단순 화면"; allowed -> "AAOS UX 상세 허용 · 실제 속도/기어 판정 아님"; else -> "AAOS UX 제한 · 단순 화면/짧은 코칭" })
    }
    fun start() { active=true;handler.post(check) }
    private val check = object : Runnable {
        override fun run() {
            if(!active) return
            try {
                if(car?.isConnected != true) {
                    apply(null)
                    try { manager?.unregisterListener() } catch(_: Exception) {}
                    car?.disconnect()
                    car=Car.createCar(context)
                    manager=car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                    manager?.registerListener(listener)
                }
                apply(manager?.currentCarUxRestrictions)
            } catch(_: Exception) { apply(null) }
            handler.postDelayed(this, 2000)
        }
    }
    fun stop() { active=false;handler.removeCallbacksAndMessages(null);try { manager?.unregisterListener();car?.disconnect() } catch(_: Exception) {};manager=null;car=null;apply(null) }
}
