package com.example.localtaskassistant.capture.ocr

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.widget.*
import android.view.WindowManager
import com.example.localtaskassistant.bootstrap.AssistantApplication
import com.example.localtaskassistant.contract.TargetApps
import kotlinx.coroutines.*

/** A目录内临时验证入口；不改C的正式界面，不自动替用户同意系统授权。 */
class WechatProjectionProbeActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var summary: TextView
    private lateinit var startButton: Button
    private lateinit var selfTestButton: Button
    private lateinit var selfTestOutput: TextView
    private val diagnosticScope = CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var selfTestJob: Job?=null
    private var controlledEntireScreen = false
    private var previewMode=false
    private var bandComparison=false
    private var debugExport=false
    private var chatDarkText=false
    private var manualTrigger=false
    private var previewDialog: Dialog?=null
    private val previewScreenOff=object: BroadcastReceiver() {
        override fun onReceive(context: Context?,intent: Intent?) {
            if(intent?.action==Intent.ACTION_SCREEN_OFF) { previewDialog?.dismiss(); DebugFramePreview.clear() }
        }
    }
    private var awaitingConsent = false
    private var note = "请先在助手设置中开启识别并选择微信，同时开启无障碍服务。"
    private val refresh = object : Runnable {
        override fun run() { render(); ui.postDelayed(this, 1000) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        registerReceiver(previewScreenOff,IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED)
        awaitingConsent = savedInstanceState?.getBoolean("awaitingConsent") ?: false
        previewMode=savedInstanceState?.getBoolean("previewMode") ?: intent.getBooleanExtra("a_preview_mode",false)
        bandComparison=savedInstanceState?.getBoolean("bandComparison") ?: intent.getBooleanExtra("a_band_comparison",false)
        debugExport=savedInstanceState?.getBoolean("debugExport") ?: intent.getBooleanExtra("a_debug_export",false)
        chatDarkText=savedInstanceState?.getBoolean("chatDarkText") ?: intent.getBooleanExtra("a_chat_dark_text",false)
        manualTrigger=savedInstanceState?.getBoolean("manualTrigger") ?: intent.getBooleanExtra("a_manual_frame_trigger",false)
        controlledEntireScreen = savedInstanceState?.getBoolean("controlledEntireScreen")
            ?: intent.getBooleanExtra("a_controlled_entire_screen", false)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        column.addView(TextView(this).apply {
            textSize = 22f; text = "微信文字识别 · 验证"
        })
        column.addView(TextView(this).apply {
            text = (if(debugExport) "【已授权测试导出】本次会保存一帧原图/RGBA供调试检查，不写入任务库。\n\n"
                else "本入口始终运行本地文字识别，对照原图/黑白增强。不提取任务，不保存图片或识字原文。\n\n") +
                (if (controlledEntireScreen) "本次为已同意的整屏对照：请在系统窗口选择“整个屏幕”，随后只停留在三句测试聊天。\n\n"
                 else "仅支持本次Android 14及以上验证。请在系统窗口选择“共享单个应用”，并选择微信。\n\n") +
                (if(manualTrigger) "本轮先授权并准备测试页面，再由电脑触发取帧；最长5分钟。可在通知中停止。"
                 else "在微信自己的测试聊天/测试文章中保持几秒即可自动取帧，最长2分钟。可用通知中的“停止”结束。")
        })
        column.addView(Button(this).apply {
            text = "打开助手设置入口"
            setOnClickListener { startActivity(Intent().setClassName(packageName, "$packageName.presentation.MainActivity")) }
        })
        column.addView(Button(this).apply {
            text = "打开无障碍设置"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        startButton = Button(this).apply {
            text = if(manualTrigger) "先授权，页面准备好后取帧" else if(previewMode && bandComparison) "单帧整图／分块对照" else if(previewMode) "取一帧并在本机预览" else "开始微信文字识别验证"
            setOnClickListener { requestCapture() }
        }
        column.addView(startButton)
        if(previewMode) column.addView(TextView(this).apply {
            text=if(debugExport) "本次调试会导出一帧PNG和RGBA，完成一组OCR后自动停止共享。普通入口默认关闭导出。"
                else "本次只保留一帧供你在手机查看。完成一组OCR后自动停止共享；回到此页查看。内存预览最长60秒，关闭或锁屏即清除，不保存、不上传。"
        })
        column.addView(TextView(this).apply {
            text="文字验证请在自己控制的微信测试聊天中准备三句，并保持同屏：\n明天下午3点提交实验报告\n地点信息楼302\n周五18点30分前完成报名\n\n原图和增强分组记录；白底增强仍属实验，不代表深色页面效果。"
        })
        column.addView(Button(this).apply {
            text = "停止验证"
            setOnClickListener {
                startService(Intent(this@WechatProjectionProbeActivity, WechatProjectionProbeService::class.java)
                    .setAction(WechatProjectionProbeService.ACTION_STOP))
                note = "已请求停止。再次开始需要重新同意系统授权。"
            }
        })
        selfTestButton=Button(this).apply {
            text="合成图管线自测（不读取屏幕）"
            setOnClickListener { runSyntheticTest() }
        }
        column.addView(selfTestButton)
        selfTestOutput=TextView(this).apply { text="合成图自测只检查转换和模型，不代表微信识字效果。" }
        column.addView(selfTestOutput)
        summary = TextView(this).apply { textSize = 16f }
        column.addView(summary)
        setContentView(ScrollView(this).apply { addView(column) })
        // 调试工具只能打开系统授权窗口；授权与共享范围仍由用户手动决定。
        val autoRequest = intent.getBooleanExtra("a_auto_request_ocr", false)
        intent.removeExtra("a_auto_request_ocr")
        if (autoRequest && savedInstanceState == null) ui.post { requestCapture() }
        val autoSynthetic=intent.getBooleanExtra("a_run_synthetic_ocr",false)
        intent.removeExtra("a_run_synthetic_ocr")
        if(autoSynthetic && !autoRequest && savedInstanceState==null) ui.post { runSyntheticTest() }
        val autoScreenSize=intent.getBooleanExtra("a_run_screen_size_test",false)
        intent.removeExtra("a_run_screen_size_test")
        if(autoScreenSize && !autoRequest && !autoSynthetic && savedInstanceState==null)
            ui.post { runSyntheticTest(screenSized=true) }
        val autoImported=intent.getBooleanExtra("a_run_imported_ocr",false)
        intent.removeExtra("a_run_imported_ocr")
        if(autoImported && !autoRequest && !autoSynthetic && !autoScreenSize && savedInstanceState==null)
            ui.post { runSyntheticTest(imported=true) }
    }
    private fun runSyntheticTest(screenSized: Boolean=false,imported: Boolean=false) {
        if(selfTestJob?.isActive==true || ProjectionProbeReports.latest?.phase in
            setOf(ProjectionProbePhase.PREPARING,ProjectionProbePhase.RUNNING)) return
        selfTestJob=diagnosticScope.launch {
            selfTestOutput.text=if(imported) "正在识别已授权导入的测试截图……" else "正在识别内存中生成的三句测试文字……"
            try {
                val result=if(imported) OcrDebugImageTest.run(this@WechatProjectionProbeActivity,chatDarkText)
                    else if(screenSized) OcrScreenSizeExperiment.run(this@WechatProjectionProbeActivity)
                    else OcrPipelineSelfTest.run(this@WechatProjectionProbeActivity)
                if(imported) {
                    val variants=result.getJSONArray("variants")
                    selfTestOutput.text="测试截图识字：${result.optString("phase")}\n"+
                        (0 until variants.length()).joinToString("\n") { index ->
                            val item=variants.getJSONObject(index)
                            "${item.getString("mode")}：${item.getInt("hits")}/3\n${item.getString("text")}"
                        }
                } else if(screenSized) {
                    val cases=result.getJSONArray("cases")
                    selfTestOutput.text="合成图尺寸对照：${result.optString("phase")}\n"+
                        (0 until cases.length()).joinToString("\n") { index ->
                            val item=cases.getJSONObject(index); val variants=item.getJSONArray("variants")
                            "高度${item.getInt("height")}：\n"+(0 until variants.length()).joinToString("\n") { v ->
                                val variant=variants.getJSONObject(v)
                                "${variant.getString("mode")} ${variant.getInt("hits")}/3"
                            }
                        }+"\n只识别合成图，不等于微信通过。"
                } else {
                    val variants=result.optJSONArray("variants")
                    selfTestOutput.text="合成图：${result.optString("phase")}\n"+
                        (0 until (variants?.length() ?: 0)).joinToString("\n") { index ->
                            val item=variants!!.getJSONObject(index)
                            "${item.getString("mode")}：${item.getInt("fixtureHits")}/3（${item.getString("phase")}）"
                        }+"\n不等于微信实测通过。"
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { selfTestOutput.text="合成图自测异常，未判定通过。" }
        }
    }
    private fun requestCapture() {
        if (Build.VERSION.SDK_INT < 34) { note = "本次验证入口仅支持Android 14及以上。"; render(); return }
        if (awaitingConsent) return
        if(selfTestJob?.isActive==true) { note="合成图自测运行中，请完成后再开始共享。"; return }
        val capture=(application as AssistantApplication).container.capture.status.value
        if(!capture.accessibilityConnected || !capture.policy.enabled || TargetApps.WECHAT !in capture.policy.allowedPackages) {
            note="请先开启读屏服务，并在助手设置开启识别、选择微信。当前还没有请求系统录屏授权。"
            render(); return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            note = "请允许通知，以便随时从通知栏停止。允许后再次点击开始。"
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 204)
            return
        }
        val current = ProjectionProbeReports.latest
        if (current?.phase in setOf(ProjectionProbePhase.RUNNING, ProjectionProbePhase.PREPARING)) return
        awaitingConsent = true
        try {
            // 使用用户选择模式，不强制全屏共享，不保存授权结果。
            startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), 202)
        } catch (_: RuntimeException) {
            awaitingConsent = false
            note = "无法打开系统授权窗口，尚未开始取画面。"
        }
    }
    @Deprecated("临时验证入口保留平台回调，不新增依赖")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 202) return
        awaitingConsent = false
        if (resultCode != RESULT_OK || data == null) { note = "已取消系统授权，本次未开始采集。"; return }
        try {
            startForegroundService(Intent(this, WechatProjectionProbeService::class.java)
                .putExtra(WechatProjectionProbeService.EXTRA_RESULT, resultCode)
                .putExtra(WechatProjectionProbeService.EXTRA_OCR,true)
                .putExtra(WechatProjectionProbeService.EXTRA_PREVIEW,previewMode)
                .putExtra(WechatProjectionProbeService.EXTRA_BANDS,previewMode && bandComparison)
                .putExtra(WechatProjectionProbeService.EXTRA_DEBUG_EXPORT,previewMode && debugExport)
                .putExtra(WechatProjectionProbeService.EXTRA_CHAT_BINARY,chatDarkText)
                .putExtra(WechatProjectionProbeService.EXTRA_MANUAL_TRIGGER,manualTrigger)
                .putExtra(WechatProjectionProbeService.EXTRA_CONSENT, data))
            note = "已提交本次授权。请保持微信测试页面可见；可在通知中停止。"
        } catch (_: RuntimeException) { note = "启动失败，未确认采集成功。请查看声明和系统限制。" }
    }
    private fun render() {
        if (!::summary.isInitialized) return
        val report = ProjectionProbeReports.latest
        val sharing=report?.phase in setOf(ProjectionProbePhase.PREPARING, ProjectionProbePhase.RUNNING)
        startButton.isEnabled = !awaitingConsent && !sharing && selfTestJob?.isActive!=true
        selfTestButton.isEnabled = !awaitingConsent && !sharing && selfTestJob?.isActive!=true
        summary.text = note + "\n\n" + if (report == null) "本进程尚无采集记录。" else
            "状态：${report.phase}\n有效接收帧：${report.frames}\n有亮度变化的帧：${report.variedFrames}\n" +
                "最近尺寸：${report.width} × ${report.height}\n范围不符跳过：${report.scopeSkips}\n结束原因：${report.endReason ?: "—"}\n\n" +
                "像素指标不代表文字可读；仅下方同会话的文字对照属于OCR结果。"
        val ocr=WechatOcrProbeReports.latest?.takeIf { it.runId==report?.runId }
        if(ocr!=null) summary.append("\n\n文字对照：${ocr.phase}\n完成帧对：${ocr.pairs}\n"+
            "同一最佳帧：原图${ocr.bestPairRawHits}/3，增强${ocr.bestPairBinaryHits}/3\n"+
            "最近尺寸：${ocr.width}×${ocr.height}\n忙碌跳过：${ocr.busyDrops}；错误：${ocr.errors}\n未接任务提取。")
        ocr?.inputDiagnostics?.let { input ->
            summary.append("\n中央像素抽样：${input.center.samples}；全透明：${input.center.alphaZero}；"+
                "不透明：${input.center.alphaOpaque}\n中央亮度：${input.center.minimumLuminance}～${input.center.maximumLuminance}；"+
                "仅聚合统计，不代表文字可读。")
        }
        ocr?.bandComparison?.let { result ->
            summary.append("\n同一帧分块：原图${result.raw.hits}/3，增强${result.binary?.hits ?: "—"}/3；"+
                "仅测试指标，未接业务提取。")
        }
        if(ocr!=null && ocr.pairs>0 && ocr.rawCharacters<4)
            summary.append("\n未读出足够文字。本机已验证屏幕共享防护可能模糊输入，请检查系统共享状态；0字不代表没有任务。")
        showLocalPreview()
    }
    private fun showLocalPreview() {
        if(previewDialog!=null || isFinishing || isDestroyed) return
        val image=DebugFramePreview.take(this) ?: return
        val remaining=image.expiresAt-SystemClock.elapsedRealtime()
        if(remaining<=0) { image.bitmap.recycle(); return }
        val dialog=Dialog(this)
        previewDialog=dialog
        val column=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(16,16,16,16) }
        column.addView(TextView(this).apply {
            text="这是刚才OCR实际使用的原始画面。请核对是否是三句聊天、文字是否清楚。无需截图或上传。"
        })
        val picture=ImageView(this).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            scaleType=ImageView.ScaleType.FIT_CENTER
            setImageBitmap(image.bitmap)
        }
        column.addView(picture,LinearLayout.LayoutParams(-1,0,1f))
        column.addView(Button(this).apply { text="关闭并清除画面"; setOnClickListener { dialog.dismiss() } })
        dialog.setContentView(column)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val expire=Runnable { dialog.dismiss() }
        dialog.setOnDismissListener {
            picture.setImageDrawable(null); image.bitmap.recycle()
            ui.removeCallbacks(expire); previewDialog=null
        }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT)
        ui.postDelayed(expire,remaining)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        // 只保存界面等待状态和本次提示，不保存或复用系统授权Intent/token。
        outState.putBoolean("awaitingConsent", awaitingConsent)
        outState.putBoolean("controlledEntireScreen", controlledEntireScreen)
        outState.putBoolean("previewMode",previewMode)
        outState.putBoolean("bandComparison",bandComparison)
        outState.putBoolean("debugExport",debugExport)
        outState.putBoolean("chatDarkText",chatDarkText)
        outState.putBoolean("manualTrigger",manualTrigger)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); ui.removeCallbacks(refresh); ui.post(refresh) }
    override fun onPause() { ui.removeCallbacks(refresh); previewDialog?.dismiss(); super.onPause() }
    override fun onDestroy() {
        previewDialog?.dismiss(); diagnosticScope.cancel()
        try { unregisterReceiver(previewScreenOff) } catch(_: IllegalArgumentException) { }
        super.onDestroy()
    }
}
