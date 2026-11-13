package com.example.localtaskassistant.capture.ocr

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AtomicFile
import com.example.localtaskassistant.contract.OcrFrame
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 仅合成图：固定宽度、字体和文字，只改变画布高度；四种模式均读取同一帧。 */
internal object OcrScreenSizeExperiment {
    suspend fun run(context: Context): JSONObject=withContext(Dispatchers.Default) {
        val report=JSONObject().put("runId","A022e_"+UUID.randomUUID().toString().replace("-",""))
            .put("phase","RUNNING").put("source","SYNTHETIC_HEIGHT_COMPARISON")
            .put("fixtureTotal",3).put("fontPixels",52).put("width",1152).put("experimentVersion",2)
        val cases=JSONArray(); report.put("cases",cases); publish(context,report)
        val began=SystemClock.elapsedRealtime()
        var cleared=true
        try {
            for(height in listOf(480,2560)) {
                val frame=createFrame(height)
                try {
                    val variants=JSONArray()
                    cases.put(JSONObject().put("height",height).put("variants",variants))
                    for((mode,tiled,binary) in listOf(
                        Triple("FULL_RAW",false,false),Triple("FULL_BINARY",false,true),
                        Triple("BANDS_RAW",true,false),Triple("BANDS_BINARY",true,true))) {
                        currentCoroutineContext().ensureActive()
                        val measure=OcrBandRecognition.measure(frame,binary,tiled)
                        variants.put(JSONObject().put("mode",mode).put("hits",measure.hits).put("characters",measure.characters)
                            .put("bands",measure.bands).put("elapsedMillis",measure.elapsedMillis)
                            .put("phase",if(measure.failed) "ERROR" else if(measure.hits==3) "PASS" else "FAIL"))
                        publish(context,report)
                        if(measure.failed) throw IllegalStateException("合成图对照未完成")
                    }
                } finally { frame.bytes.fill(0); cleared=cleared && frame.bytes.all { it==0.toByte() } }
            }
            report.put("phase","COMPLETE")
        } catch(cancelled: CancellationException) { report.put("phase","CANCELLED"); throw cancelled }
        catch(_: Exception) { report.put("phase","ERROR") }
        finally {
            report.put("inputBytesCleared",cleared).put("elapsedMillis",SystemClock.elapsedRealtime()-began)
            publish(context,report)
        }
        report
    }
    private fun createFrame(height: Int): OcrFrame {
        val width=1152; val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val bytes=ByteArray(width*height*4); val row=IntArray(width)
        try {
            val canvas=Canvas(bitmap); canvas.drawColor(Color.WHITE)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK; textSize=52f; typeface=Typeface.DEFAULT }
            FIXTURES.forEachIndexed { index,text -> canvas.drawText(text,40f,100f+index*140f,paint) }
            for(y in 0 until height) {
                bitmap.getPixels(row,0,width,0,y,width,1)
                for(x in 0 until width) {
                    val at=(y*width+x)*4; val color=row[x]
                    bytes[at]=(color ushr 16).toByte(); bytes[at+1]=(color ushr 8).toByte()
                    bytes[at+2]=color.toByte(); bytes[at+3]=(color ushr 24).toByte()
                }
            }
            return OcrFrame(bytes,width,height)
        } catch(error: Exception) { bytes.fill(0); throw error }
        finally { row.fill(0); bitmap.recycle() }
    }
    private fun publish(context: Context,report: JSONObject) {
        val directory=File(context.filesDir,"ocr-screen-size-experiment").apply { mkdirs() }
        val file=AtomicFile(File(directory,"report.json")); val stream=file.startWrite()
        try { stream.write(report.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch(error: Exception) { file.failWrite(stream); throw error }
    }
    private val FIXTURES=listOf("明天下午3点提交实验报告","地点信息楼302","周五18点30分前完成报名")
}
