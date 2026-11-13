package com.example.localtaskassistant.capture.ocr

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import com.example.localtaskassistant.contract.OcrFrame
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

internal data class WechatOcrSnapshot(
    val runId: String, val phase: String="RUNNING", val stopReason: String?=null,
    val pairs: Int=0, val errors: Int=0, val busyDrops: Int=0,
    val lastRawHits: Int=0, val lastBinaryHits: Int=0,
    val bestPairRawHits: Int=0, val bestPairBinaryHits: Int=0, val bestPairNumber: Int=0,
    val width: Int=0, val height: Int=0, val lastPairMillis: Long=0,
    val rawCharacters: Int=0, val binaryCharacters: Int=0,
    val inputDiagnostics: OcrInputDiagnostic?=null,
    val bandComparison: OcrBandComparison?=null,
    val binaryMode: String="WHITE_PAGE_200",
)

internal data class OcrInputDiagnostic(
    val rowStride: Int, val pixelStride: Int, val bufferPosition: Int, val bufferRemaining: Int,
    val full: OcrPixelSummary, val center: OcrPixelSummary,
)

internal fun pixelSummaryJson(value: OcrPixelSummary): JSONObject = JSONObject()
    .put("samples",value.samples).put("alphaZero",value.alphaZero).put("alphaOpaque",value.alphaOpaque)
    .put("minimumAlpha",value.minimumAlpha).put("maximumAlpha",value.maximumAlpha)
    .put("darkSamples",value.darkSamples).put("lightSamples",value.lightSamples)
    .put("minimumLuminance",value.minimumLuminance).put("maximumLuminance",value.maximumLuminance)
    .put("meanLuminance",value.meanLuminance)

internal fun bandMeasureJson(value: OcrBandMeasure): JSONObject=JSONObject()
    .put("hits",value.hits).put("characters",value.characters).put("bands",value.bands)
    .put("elapsedMillis",value.elapsedMillis).put("failed",value.failed)

internal object WechatOcrProbeReports {
    @Volatile var latest: WechatOcrSnapshot?=null
        private set
    @Synchronized fun publish(context: Context, value: WechatOcrSnapshot) {
        latest=value
        val json=JSONObject().put("runId",value.runId).put("phase",value.phase)
            .put("stopReason",value.stopReason ?: JSONObject.NULL).put("pairs",value.pairs)
            .put("errors",value.errors).put("busyDrops",value.busyDrops)
            .put("lastRawHits",value.lastRawHits).put("lastBinaryHits",value.lastBinaryHits)
            .put("bestPairRawHits",value.bestPairRawHits).put("bestPairBinaryHits",value.bestPairBinaryHits)
            .put("bestPairNumber",value.bestPairNumber).put("width",value.width).put("height",value.height)
            .put("lastPairMillis",value.lastPairMillis).put("rawCharacters",value.rawCharacters)
            .put("binaryCharacters",value.binaryCharacters).put("fixtureTotal",3)
            .put("binaryMode",value.binaryMode)
        value.inputDiagnostics?.let { input ->
            json.put("inputDiagnostics",JSONObject().put("rowStride",input.rowStride).put("pixelStride",input.pixelStride)
                .put("bufferPosition",input.bufferPosition).put("bufferRemaining",input.bufferRemaining)
                .put("full",pixelSummaryJson(input.full)).put("center",pixelSummaryJson(input.center)))
        }
        value.bandComparison?.let { result ->
            json.put("bandComparison",JSONObject().put("raw",bandMeasureJson(result.raw))
                .put("binary",result.binary?.let { bandMeasureJson(it) } ?: JSONObject.NULL))
        }
        try {
            val directory=File(context.filesDir,"wechat-ocr-probe").apply { mkdirs() }
            val file=AtomicFile(File(directory,"report.json"))
            val stream=file.startWrite()
            try { stream.write(json.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
            catch (_: Exception) { file.failWrite(stream) }
        } catch (_: Exception) { /* 没有新报告不得当作通过；不记录原文。 */ }
    }
}

/**
 * 仅开发验证：对同一真实微信帧做原图/白底增强，报告计数，不交付任务库。
 * allowed需同时校验捕获会话epoch、识别开关/白名单、微信前台、服务未关闭。
 */
internal class WechatOcrProbe(
    private val context: Context, runId: String,
    private val compareBands: Boolean=false,
    private val chatDarkText: Boolean=false,
    private val onPairCompleted: ((ByteArray,Int,Int)->Unit)?=null,
    private val allowed: (Long)->Boolean,
) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val closed=AtomicBoolean(false)
    private val busy=AtomicBoolean(false)
    private val raw=LocalOcrAdapter()
    private val binary=LocalOcrAdapter(whitePageBinary=!chatDarkText,chatDarkText=chatDarkText)
    private var state=WechatOcrSnapshot(runId,binaryMode=if(chatDarkText) "CHAT_DARK_TEXT_160" else "WHITE_PAGE_200")
    init { WechatOcrProbeReports.publish(context,state) }

    fun offer(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int, epoch: Long) {
        if(closed.get() || !allowed(epoch)) return
        // 超时停止等待时SDK可能仍在运行；两适配器都空闲才接受新的一对任务。
        if(!raw.canAcceptFrame || !binary.canAcceptFrame || !busy.compareAndSet(false,true)) {
            update { it.copy(busyDrops=(it.busyDrops+1).coerceAtMost(10_000)) }; return
        }
        val bufferPosition=buffer.position()
        val bufferRemaining=buffer.remaining()
        val bytes=try { RgbaPlanePacker.pack(buffer,width,height,rowStride,pixelStride) }
        catch (_: Exception) { busy.set(false); update { it.copy(errors=it.errors+1) }; return }
        try {
            val job=scope.launch {
                if(!allowed(epoch) || closed.get()) return@launch
                val start=SystemClock.elapsedRealtime()
                val frame=OcrFrame(bytes,width,height)
                val input=OcrInputDiagnostic(rowStride,pixelStride,bufferPosition,bufferRemaining,
                    OcrFrameDiagnostics.summarize(bytes,width,height),
                    OcrFrameDiagnostics.summarize(bytes,width,height,centerOnly=true))
                update { it.copy(inputDiagnostics=input) }
                val first=raw.recognize(frame)
                currentCoroutineContext().ensureActive()
                if(!allowed(epoch) || closed.get()) return@launch
                if(first.isFailure) { update { it.copy(errors=it.errors+1) }; return@launch }
                val second=binary.recognize(frame)
                currentCoroutineContext().ensureActive()
                if(!allowed(epoch) || closed.get()) return@launch
                if(second.isFailure) { update { it.copy(errors=it.errors+1) }; return@launch }
                val rawText=first.getOrThrow().joinToString("\n") { it.text }
                val binaryText=second.getOrThrow().joinToString("\n") { it.text }
                val rawHits=hits(rawText)
                val binaryHits=hits(binaryText)
                update {
                    val pair=it.pairs+1
                    val better=binaryHits>it.bestPairBinaryHits ||
                        (binaryHits==it.bestPairBinaryHits && rawHits>it.bestPairRawHits)
                    it.copy(pairs=pair,lastRawHits=rawHits,lastBinaryHits=binaryHits,
                        bestPairRawHits=if(better) rawHits else it.bestPairRawHits,
                        bestPairBinaryHits=if(better) binaryHits else it.bestPairBinaryHits,
                        bestPairNumber=if(better) pair else it.bestPairNumber,
                        width=width,height=height,lastPairMillis=SystemClock.elapsedRealtime()-start,
                        rawCharacters=rawText.length,binaryCharacters=binaryText.length)
                }
                if(compareBands && !closed.get() && allowed(epoch)) {
                    val rawBands=OcrBandRecognition.measure(frame,binary=false,tiled=true) { !closed.get() && allowed(epoch) }
                    val binaryBands=if(rawBands.failed) null else
                        OcrBandRecognition.measure(frame,binary=true,tiled=true,chatDarkText=chatDarkText) { !closed.get() && allowed(epoch) }
                    if(closed.get() || !allowed(epoch)) return@launch
                    update { it.copy(bandComparison=OcrBandComparison(rawBands,binaryBands)) }
                }
                // 仅显式开启的本机单帧诊断；与这一组OCR使用同一份打包后的字节。
                if(!closed.get() && allowed(epoch)) {
                    try { onPairCompleted?.invoke(bytes,width,height) }
                    catch(_: Exception) { update { it.copy(errors=it.errors+1) } }
                }
            }
            // 即使launch时scope已经取消、协程体从未运行，完成钩子仍清零本次原始帧。
            job.invokeOnCompletion { bytes.fill(0); busy.set(false) }
        } catch (_: Exception) { bytes.fill(0); busy.set(false); update { it.copy(errors=it.errors+1) } }
    }
    @Synchronized private fun update(transform: (WechatOcrSnapshot)->WechatOcrSnapshot) {
        if(closed.get()) return
        state=transform(state)
        WechatOcrProbeReports.publish(context,state)
    }
    @Synchronized fun close(reason: String) {
        if(!closed.compareAndSet(false,true)) return
        scope.cancel(); raw.close(); binary.close()
        state=state.copy(phase="STOPPED",stopReason=reason)
        WechatOcrProbeReports.publish(context,state)
    }
    private fun hits(text: String): Int {
        val compact=text.replace(Regex("\\s+"),"")
        return FIXTURES.count { compact.contains(it) }
    }
    private companion object {
        val FIXTURES=listOf("明天下午3点提交实验报告","地点信息楼302","周五18点30分前完成报名")
    }
}
