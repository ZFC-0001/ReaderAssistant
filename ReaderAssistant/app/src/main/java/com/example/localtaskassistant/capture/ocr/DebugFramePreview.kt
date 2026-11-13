package com.example.localtaskassistant.capture.ocr

import android.app.KeyguardManager
import android.content.*
import android.graphics.Bitmap
import android.os.*

/** 仅debug显式单帧入口；默认仅内存，额外授权的debugExport才写私有测试文件。 */
internal object DebugFramePreview {
    data class Image(val bitmap: Bitmap,val expiresAt: Long)
    private val lease=PreviewLease()
    private val main=Handler(Looper.getMainLooper())
    private var bitmap: Bitmap?=null
    private var registered: Context?=null
    private var generation=0
    private val screenOff=object: BroadcastReceiver() {
        override fun onReceive(context: Context?,intent: Intent?) { if(intent?.action==Intent.ACTION_SCREEN_OFF) clear() }
    }
    @Synchronized fun arm(context: Context,runId: String) {
        clear()
        val app=context.applicationContext
        app.registerReceiver(screenOff,IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED)
        registered=app; lease.arm(runId,SystemClock.elapsedRealtime())
        val current=++generation
        main.postDelayed({ synchronized(this) { if(current==generation) clear() } },60_000)
    }
    @Synchronized fun offer(context: Context,runId: String,bytes: ByteArray,width: Int,height: Int,debugExport: Boolean=false): Boolean {
        if(!lease.canPublish(runId,SystemClock.elapsedRealtime()) || !unlocked(context)) return false
        OcrRgbaPixels.validate(bytes,width,height)
        val image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val row=IntArray(width)
        try {
            for(y in 0 until height) {
                OcrRgbaPixels.argbRow(bytes,width,y,row)
                image.setPixels(row,0,width,0,y,width,1)
            }
            if(!unlocked(context) || !lease.publish(runId,SystemClock.elapsedRealtime())) { image.recycle(); return false }
            if(debugExport) DebugFrameExport.write(context,runId,image,bytes,width,height)
            bitmap=image
            return true
        } catch(error: Exception) { image.recycle(); throw error }
        finally { row.fill(0) }
    }
    @Synchronized fun take(context: Context): Image? {
        if(!unlocked(context)) { clear(); return null }
        val image=bitmap ?: return null
        val deadline=lease.expiresAt
        if(!lease.take(SystemClock.elapsedRealtime())) { clear(); return null }
        bitmap=null; releaseReceiver(); generation++; main.removeCallbacksAndMessages(null)
        return Image(image,deadline)
    }
    @Synchronized fun clear() {
        bitmap?.recycle(); bitmap=null; lease.clear(); generation++
        main.removeCallbacksAndMessages(null); releaseReceiver()
    }
    private fun releaseReceiver() {
        registered?.let { try { it.unregisterReceiver(screenOff) } catch(_: IllegalArgumentException) { } }
        registered=null
    }
    private fun unlocked(context: Context)=context.getSystemService(PowerManager::class.java).isInteractive &&
        !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
}
