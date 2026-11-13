package com.example.localtaskassistant.capture.ocr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.widget.*
import com.example.localtaskassistant.bootstrap.AssistantApplication
import com.example.localtaskassistant.contract.TargetApps

/** A目录内临时验证入口；不改C的正式界面，不自动替用户同意系统授权。 */
class WechatProjectionProbeActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var summary: TextView
    private lateinit var startButton: Button
    private lateinit var ocrButton: Button
    private var requestOcr=false
    private var awaitingConsent = false
    private var note = "请先在助手设置中开启识别并选择微信，同时开启无障碍服务。"
    private val refresh = object : Runnable {
        override fun run() { render(); ui.postDelayed(this, 1000) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) { finish(); return }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        column.addView(TextView(this).apply {
            textSize = 22f; text = "微信自动取画面 · 验证"
        })
        column.addView(TextView(this).apply {
            text = "画面验证只测取帧；文字验证会在本地对照原图/黑白增强。不提取任务，不保存图片或识字原文。\n\n" +
                "仅支持本次Android 14及以上验证。开始后请在系统窗口选择“共享单个应用”，并选择微信；不要选择整个屏幕。\n\n" +
                "在微信自己的测试聊天/测试文章中保持几秒即可自动取帧，最长2分钟。可用通知中的“停止”结束。"
        })
        column.addView(Button(this).apply {
            text = "打开助手设置入口"
            setOnClickListener { startActivity(Intent().setClassName(packageName, "$packageName.presentation.MainActivity")) }
        })
        column.addView(Button(this).apply {
            text = "打开无障碍设置"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        startButton = Button(this).apply { text = "开始验证（系统将请求授权）"; setOnClickListener { requestCapture() } }
        column.addView(startButton)
        ocrButton=Button(this).apply { text="开始微信文字识别验证"; setOnClickListener { requestCapture(ocr=true) } }
        column.addView(ocrButton)
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
        summary = TextView(this).apply { textSize = 16f }
        column.addView(summary)
        setContentView(ScrollView(this).apply { addView(column) })
    }
    private fun requestCapture(ocr: Boolean=false) {
        if (Build.VERSION.SDK_INT < 34) { note = "本次验证入口仅支持Android 14及以上。"; render(); return }
        if (awaitingConsent) return
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
        requestOcr=ocr
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
                .putExtra(WechatProjectionProbeService.EXTRA_OCR,requestOcr)
                .putExtra(WechatProjectionProbeService.EXTRA_CONSENT, data))
            note = "已提交本次授权。请保持微信测试页面可见；可在通知中停止。"
        } catch (_: RuntimeException) { note = "启动失败，未确认采集成功。请查看声明和系统限制。" }
    }
    private fun render() {
        if (!::summary.isInitialized) return
        val report = ProjectionProbeReports.latest
        startButton.isEnabled = !awaitingConsent && report?.phase !in setOf(ProjectionProbePhase.PREPARING, ProjectionProbePhase.RUNNING)
        ocrButton.isEnabled=startButton.isEnabled
        summary.text = note + "\n\n" + if (report == null) "本进程尚无采集记录。" else
            "状态：${report.phase}\n有效接收帧：${report.frames}\n有亮度变化的帧：${report.variedFrames}\n" +
                "最近尺寸：${report.width} × ${report.height}\n范围不符跳过：${report.scopeSkips}\n结束原因：${report.endReason ?: "—"}\n\n" +
                "像素指标不代表文字可读。需要后续本地OCR验证。"
        val ocr=WechatOcrProbeReports.latest?.takeIf { it.runId==report?.runId }
        if(ocr!=null) summary.append("\n\n文字对照：${ocr.phase}\n完成帧对：${ocr.pairs}\n"+
            "同一最佳帧：原图${ocr.bestPairRawHits}/3，增强${ocr.bestPairBinaryHits}/3\n"+
            "最近尺寸：${ocr.width}×${ocr.height}\n忙碌跳过：${ocr.busyDrops}；错误：${ocr.errors}\n未接任务提取。")
    }
    override fun onResume() { super.onResume(); ui.removeCallbacks(refresh); ui.post(refresh) }
    override fun onPause() { ui.removeCallbacks(refresh); super.onPause() }
}
