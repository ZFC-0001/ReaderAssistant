package com.example.localtaskassistant.capture.ocr

import android.app.*
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import java.io.FileDescriptor
import java.io.File
import java.io.PrintWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import com.example.localtaskassistant.bootstrap.AssistantApplication
import kotlin.math.sqrt

/**
 * A临时真机验证：系统授权/自动取帧/本地OCR，不接任务流程。
 * 必须先由用户在系统对话框选择“单个应用→微信”；SDK不能反查所选应用身份。
 * 还需无障碍元数据门禁：仅前台为微信、已选择微信且识别开启时抽样像素。
 */
class WechatProjectionProbeService : Service() {
    private val workerThread = HandlerThread("A-Wechat-projection")
    private lateinit var worker: Handler
    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val screenReceiverRegistered = AtomicBoolean(false)
    private var session: ProjectionProbeSession? = null
    private var ocrEnabled=false
    private var previewEnabled=false
    private var manualTrigger=false
    private var previewArmed=false
    private var currentRunId: String?=null
    private val triggerFile get()=File(filesDir,"wechat-probe-control/accept-next-frame")
    @Volatile private var previewReady=false
    private var ocrProbe: WechatOcrProbe?=null
    private val captureBus get()=(application as AssistantApplication).container.capture
    @Volatile private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var contentVisible = false
    private var lastAttempt = Long.MIN_VALUE
    private var warmupFrames = 2
    private var captureWidth = 0
    private var captureHeight = 0
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) stop(ProjectionProbeEnd.LOCKED)
        }
    }
    private val screenWatchdog = object : Runnable {
        override fun run() {
            if (closed.get()) return
            // 主线程只查系统状态，不取节点或画面；不依赖ImageReader再发出一帧。
            val interactive = getSystemService(PowerManager::class.java).isInteractive
            val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if (ProjectionScreenGuard.mustStop(interactive, locked)) stop(ProjectionProbeEnd.LOCKED)
            else main.postDelayed(this, 500)
        }
    }
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { stop(ProjectionProbeEnd.SYSTEM_STOP) }
        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            contentVisible = isVisible
            warmupFrames = 2
        }
        override fun onCapturedContentResize(width: Int, height: Int) {
            if (closed.get() || display == null || width <= 0 || height <= 0) return
            val (w, h) = boundedSize(width, height)
            if (w == captureWidth && h == captureHeight) return
            try {
                val old = reader
                val replacement = createReader(w, h)
                display?.resize(w, h, resources.configuration.densityDpi)
                display?.surface = replacement.surface
                reader = replacement
                captureWidth = w
                captureHeight = h
                old?.close()
                warmupFrames = 2
            } catch (_: RuntimeException) { stop(ProjectionProbeEnd.FRAME_FAILED) }
        }
    }
    override fun onCreate() {
        super.onCreate()
        workerThread.start()
        worker = Handler(workerThread.looper)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stop(ProjectionProbeEnd.USER_STOP); return START_NOT_STICKY }
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0 || Build.VERSION.SDK_INT < 34) {
            stopSelf(); return START_NOT_STICKY
        }
        // 不接受重复授权启动，不缓存/落盘/重用授权Intent，进程重启不自动续录。
        if (session != null) return START_NOT_STICKY
        val data = intent?.getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
        if (intent?.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED) != Activity.RESULT_OK || data == null) {
            stopSelf(); return START_NOT_STICKY
        }
        val active = ProjectionProbeSession("A02_" + UUID.randomUUID().toString().replace("-", ""), SystemClock.elapsedRealtime())
        session = active
        ocrEnabled=intent.getBooleanExtra(EXTRA_OCR,true)
        previewEnabled=intent.getBooleanExtra(EXTRA_PREVIEW,false)
        manualTrigger=previewEnabled && intent.getBooleanExtra(EXTRA_MANUAL_TRIGGER,false)
        val runId=active.snapshot(SystemClock.elapsedRealtime()).runId
        currentRunId=runId
        if(manualTrigger) {
            try { triggerFile.parentFile?.mkdirs(); triggerFile.delete() }
            catch(_: RuntimeException) { stop(ProjectionProbeEnd.START_FAILED); return START_NOT_STICKY }
        }
        if(previewEnabled && !manualTrigger) { DebugFramePreview.arm(this,runId); previewArmed=true }
        else DebugFramePreview.clear()
        val compareBands=previewEnabled && intent.getBooleanExtra(EXTRA_BANDS,false)
        val debugExport=previewEnabled && intent.getBooleanExtra(EXTRA_DEBUG_EXPORT,false)
        val chatDarkText=intent.getBooleanExtra(EXTRA_CHAT_BINARY,false)
        if(ocrEnabled) ocrProbe=WechatOcrProbe(this,runId,compareBands=compareBands,chatDarkText=chatDarkText,onPairCompleted={ bytes,width,height ->
            if(previewEnabled && !closed.get() && DebugFramePreview.offer(this,runId,bytes,width,height,debugExport)) {
                previewReady=true
                main.post { stop(ProjectionProbeEnd.PREVIEW_READY) }
            }
        }) { epoch ->
            !closed.get() && captureBus.isCurrentSession(epoch) && WechatProjectionAccess.isWechatForeground()
        }
        ProjectionProbeReports.publish(this, active)
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "微信画面验证", NotificationManager.IMPORTANCE_LOW))
            val stopAction = PendingIntent.getService(this, 902,
                Intent(this, WechatProjectionProbeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
            val open = PendingIntent.getActivity(this, 903, Intent(this, WechatProjectionProbeActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val notice = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(if(previewEnabled) "单帧本机预览诊断中" else if (ocrEnabled) "微信文字识别验证中" else "仅取画面验证（不识字）")
                .setContentText(if(manualTrigger) "等待测试页面就绪后取帧，最长5分钟，可随时停止"
                    else if(debugExport) "已授权导出一帧测试图，完成后自动停止" else "本地自动取帧，不保存图片；最多2分钟，可随时停止")
                .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "停止", stopAction).build()).build()
            startForeground(901, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
            screenReceiverRegistered.set(true)
            main.post(screenWatchdog)
            // 必须先完成系统同意并启动mediaProjection类型前台服务，再取得投影实例。
            val approved = getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK, data)
            projection = approved
            worker.post { initialize(approved, active) }
            main.postDelayed({ stop(ProjectionProbeEnd.TIME_LIMIT) }, if(manualTrigger) 300_000 else 120_000)
        } catch (_: RuntimeException) { stop(ProjectionProbeEnd.START_FAILED) }
        return START_NOT_STICKY
    }
    private fun initialize(approved: MediaProjection, active: ProjectionProbeSession) {
        if (closed.get()) return
        try {
            approved.registerCallback(callback, worker)
            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val (w, h) = boundedSize(bounds.width(), bounds.height())
            captureWidth = w
            captureHeight = h
            val output = createReader(w, h)
            reader = output
            // 每个本轮授权只创建一次VirtualDisplay，旋转使用resize及替换Surface。
            display = approved.createVirtualDisplay("A-Wechat-local-probe", w, h,
                resources.configuration.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                output.surface, null, worker)
            active.running()
            ProjectionProbeReports.publish(this, active)
        } catch (_: RuntimeException) { stop(ProjectionProbeEnd.START_FAILED) }
    }
    private fun createReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { output ->
            output.setOnImageAvailableListener({ input ->
                try {
                    val image = input.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val active = session ?: return@setOnImageAvailableListener
                        if (closed.get()) return@setOnImageAvailableListener
                        val now = SystemClock.elapsedRealtime()
                        if (lastAttempt != Long.MIN_VALUE && now - lastAttempt < 1000) return@setOnImageAvailableListener
                        lastAttempt = now
                        val power = getSystemService(PowerManager::class.java)
                        if (ProjectionScreenGuard.mustStop(power.isInteractive, getSystemService(KeyguardManager::class.java).isKeyguardLocked)) {
                            stop(ProjectionProbeEnd.LOCKED); return@setOnImageAvailableListener
                        }
                        if (!contentVisible || !WechatProjectionAccess.isWechatForeground()) {
                            warmupFrames = 2
                            active.scopeSkipped()
                            ProjectionProbeReports.publish(this, active)
                            return@setOnImageAvailableListener
                        }
                        // 丢掉恢复后最初两帧，防止跨窗口切换期间的旧缓冲参与统计。
                        if (warmupFrames > 0) { warmupFrames--; return@setOnImageAvailableListener }
                        // 仅调试：token必须匹配本次runId，先准备目标页面，再由电脑触发；不按识别命中挑帧。
                        if(manualTrigger && !triggerMatches()) return@setOnImageAvailableListener
                        val plane = image.planes.firstOrNull() ?: return@setOnImageAvailableListener
                        val signal = ProjectionPixelProbe.inspect(plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride)
                        if (!closed.get() && WechatProjectionAccess.isWechatForeground()) {
                            if(previewEnabled && !previewArmed) {
                                DebugFramePreview.arm(this,active.snapshot(now).runId)
                                previewArmed=true
                            }
                            active.frame(signal, SystemClock.elapsedRealtime())
                            ProjectionProbeReports.publish(this, active)
                            ocrProbe?.offer(plane.buffer,image.width,image.height,plane.rowStride,plane.pixelStride,
                                captureBus.status.value.session)
                        }
                    } finally { image.close() }
                } catch (_: RuntimeException) { if (!closed.get()) stop(ProjectionProbeEnd.FRAME_FAILED) }
            }, worker)
        }
    private fun stop(reason: ProjectionProbeEnd) {
        if (!closed.compareAndSet(false, true)) return
        main.removeCallbacksAndMessages(null)
        unregisterScreenReceiver()
        clearTrigger()
        ocrProbe?.close(reason.name)
        if(reason!=ProjectionProbeEnd.PREVIEW_READY) DebugFramePreview.clear()
        session?.end(reason)
        // 先撤销系统投影，不等图像工作线程完成可能阻塞的窗口元数据查询。
        // 后续资源释放仍独立执行；系统实际停止以真机诊断结果验收。
        try { projection?.stop() } catch (_: RuntimeException) { /* 释放路径会继续尝试清理。 */ }
        session?.let { ProjectionProbeReports.publish(this, it) }
        worker.post { releaseResources() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    private fun releaseResources() {
        if (!released.compareAndSet(false, true)) return
        reader?.setOnImageAvailableListener(null, null)
        display?.release()
        reader?.close()
        projection?.unregisterCallback(callback)
        projection?.stop()
        display = null; reader = null; projection = null
    }
    private fun unregisterScreenReceiver() {
        if (screenReceiverRegistered.compareAndSet(true, false)) {
            try { unregisterReceiver(screenReceiver) } catch (_: IllegalArgumentException) { /* 已撤销则不重复。 */ }
        }
    }
    override fun onDestroy() {
        closed.set(true)
        main.removeCallbacksAndMessages(null)
        unregisterScreenReceiver()
        clearTrigger()
        ocrProbe?.close(ProjectionProbeEnd.SERVICE_DESTROYED.name)
        if(!previewReady) DebugFramePreview.clear()
        session?.let { it.end(ProjectionProbeEnd.SERVICE_DESTROYED); ProjectionProbeReports.publish(this, it) }
        worker.post { releaseResources(); workerThread.quitSafely() }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            writer.println("A_WECHAT_MODE ocr=$ocrEnabled")
            writer.println("A_WECHAT_TRIGGER manual=$manualTrigger session=$currentRunId tokenMatches=${triggerMatches()}")
            writer.println("A_WECHAT_PROBE ${session?.snapshot(SystemClock.elapsedRealtime())}")
        }
    }
    private fun triggerMatches(): Boolean = try {
        currentRunId!=null && triggerFile.readText().trim()==currentRunId
    } catch(_: Exception) { false }
    private fun clearTrigger() {
        if(manualTrigger) try { triggerFile.delete() } catch(_: RuntimeException) { }
    }
    private fun boundedSize(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        // 原取帧实验保持1280；OCR用更接近原屏幕的分辨率，避免小字先被缩没。
        val maximumSide=if(ocrEnabled) 2560.0 else 1280.0
        val scale = minOf(1.0, maximumSide / maxOf(width, height),
            sqrt(OcrRgbaPixels.MAX_PIXELS.toDouble()/(width.toDouble()*height)))
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }
    companion object {
        private const val CHANNEL = "a-wechat-projection-probe"
        internal const val ACTION_STOP = "com.example.localtaskassistant.capture.STOP_WECHAT_PROBE"
        internal const val EXTRA_CONSENT = "a_projection_consent"
        internal const val EXTRA_RESULT = "a_projection_result"
        internal const val EXTRA_OCR = "a_projection_enable_ocr_comparison"
        internal const val EXTRA_PREVIEW = "a_projection_single_frame_local_preview"
        internal const val EXTRA_BANDS = "a_projection_band_comparison"
        internal const val EXTRA_DEBUG_EXPORT = "a_projection_authorized_debug_export"
        internal const val EXTRA_CHAT_BINARY = "a_projection_chat_dark_text_candidate"
        internal const val EXTRA_MANUAL_TRIGGER = "a_projection_manual_frame_trigger"
    }
}
