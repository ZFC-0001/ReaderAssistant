package com.example.localtaskassistant.capture.ocr

import android.os.SystemClock
import com.example.localtaskassistant.contract.OcrFrame
import kotlinx.coroutines.CancellationException

internal data class OcrBandMeasure(val hits: Int,val characters: Int,val bands: Int,val elapsedMillis: Long,val failed: Boolean)
internal data class OcrBandComparison(val raw: OcrBandMeasure,val binary: OcrBandMeasure?)

/** 仅三句固定测试的诊断指标；同一帧原尺寸分块，不把重叠文字送业务层或存入日志。 */
internal object OcrBandRecognition {
    suspend fun measure(frame: OcrFrame,binary: Boolean,tiled: Boolean,chatDarkText: Boolean=false,onText: ((String)->Unit)?=null,
        permitted: ()->Boolean={ true }): OcrBandMeasure {
        val began=SystemClock.elapsedRealtime()
        val bands=if(tiled) OcrBandPlanner.plan(frame.height) else listOf(OcrBand(0,frame.height))
        val adapter=LocalOcrAdapter(whitePageBinary=binary && !chatDarkText,chatDarkText=binary && chatDarkText)
        val chunks=mutableListOf<String>()
        try {
            for(band in bands) {
                if(!permitted()) throw CancellationException("本帧诊断授权范围已失效")
                val input=OcrBandPlanner.copy(frame.bytes,frame.width,frame.height,band)
                try {
                    val result=adapter.recognize(OcrFrame(input,frame.width,band.height))
                    if(!permitted()) throw CancellationException("本帧诊断授权范围已失效")
                    val text=result.getOrThrow().joinToString("\n") { it.text }
                    if(text.isNotEmpty()) chunks.add(text)
                } finally { input.fill(0) }
            }
            val text=chunks.joinToString("\n")
            val compact=text.replace(Regex("\\s+"),"")
            onText?.invoke(text)
            return OcrBandMeasure(FIXTURES.count { compact.contains(it) },text.length,bands.size,
                SystemClock.elapsedRealtime()-began,false)
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { return OcrBandMeasure(0,0,bands.size,SystemClock.elapsedRealtime()-began,true) }
        finally { adapter.close(); chunks.clear() }
    }
    private val FIXTURES=listOf("明天下午3点提交实验报告","地点信息楼302","周五18点30分前完成报名")
}
