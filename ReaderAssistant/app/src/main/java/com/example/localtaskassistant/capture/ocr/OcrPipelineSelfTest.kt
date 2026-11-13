package com.example.localtaskassistant.capture.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.net.ConnectivityManager
import android.os.SystemClock
import android.util.AtomicFile
import com.example.localtaskassistant.contract.OcrFrame
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

/** Debug入口调用：自绘固定文字→带padding的RGBA→正式packer/adapter，不访问屏幕或真实聊天。 */
internal object OcrPipelineSelfTest {
    suspend fun run(context: Context): JSONObject = withContext(Dispatchers.Default) {
        val report=JSONObject().put("runId","A022c_"+UUID.randomUUID().toString().replace("-",""))
            .put("phase","RUNNING").put("fixtureTotal",FIXTURES.size)
            .put("source","SYNTHETIC_CANVAS_PADDED_RGBA")
            .put("internetPermissionGranted",context.checkSelfPermission(Manifest.permission.INTERNET)==PackageManager.PERMISSION_GRANTED)
            .put("noActiveNetworkAtStart",context.getSystemService(ConnectivityManager::class.java).activeNetwork==null)
        publish(context,report)
        val started=SystemClock.elapsedRealtime()
        var frame: OcrFrame?=null
        try {
            frame=createFrame()
            report.put("input",pixelSummaryJson(OcrFrameDiagnostics.summarize(frame.bytes,frame.width,frame.height)))
            val variants=JSONArray()
            for((mode,enhance) in listOf("RAW" to false,"WHITE_PAGE_BINARY" to true)) {
                currentCoroutineContext().ensureActive()
                val adapter=LocalOcrAdapter(whitePageBinary=enhance)
                val began=SystemClock.elapsedRealtime()
                val result=try { adapter.recognize(frame) } finally { adapter.close() }
                val text=result.getOrNull().orEmpty().joinToString("\n") { it.text }
                val hits=FIXTURES.count { text.replace(Regex("\\s+"),"").contains(it) }
                variants.put(JSONObject().put("mode",mode).put("fixtureHits",hits)
                    .put("characters",text.length).put("elapsedMillis",SystemClock.elapsedRealtime()-began)
                    .put("phase",if(result.isFailure) "ERROR" else if(hits==FIXTURES.size) "PASS" else "FAIL"))
                if(result.isFailure) break
            }
            report.put("variants",variants).put("phase","COMPLETE")
        } catch(cancelled: CancellationException) {
            report.put("phase","CANCELLED"); throw cancelled
        } catch(_: Exception) {
            report.put("phase","ERROR")
        } finally {
            frame?.bytes?.fill(0)
            report.put("inputBytesCleared",frame?.bytes?.all { it==0.toByte() } ?: true)
                .put("elapsedMillis",SystemClock.elapsedRealtime()-started)
                .put("noActiveNetworkAtEnd",context.getSystemService(ConnectivityManager::class.java).activeNetwork==null)
            publish(context,report)
        }
        report
    }

    private fun createFrame(): OcrFrame {
        val width=1400; val height=480; val stride=width*4+16
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val padded=ByteArray(stride*height)
        val row=IntArray(width)
        try {
            val canvas=Canvas(bitmap); canvas.drawColor(Color.WHITE)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK; textSize=52f; typeface=Typeface.DEFAULT }
            FIXTURES.forEachIndexed { index,text -> canvas.drawText(text,40f,100f+index*140f,paint) }
            for(y in 0 until height) {
                bitmap.getPixels(row,0,width,0,y,width,1)
                for(x in 0 until width) {
                    val at=y*stride+x*4; val color=row[x]
                    padded[at]=(color ushr 16).toByte(); padded[at+1]=(color ushr 8).toByte()
                    padded[at+2]=color.toByte(); padded[at+3]=(color ushr 24).toByte()
                }
            }
            return OcrFrame(RgbaPlanePacker.pack(ByteBuffer.wrap(padded),width,height,stride,4),width,height)
        } finally { padded.fill(0); row.fill(0); bitmap.recycle() }
    }
    private fun publish(context: Context,report: JSONObject) {
        val directory=File(context.filesDir,"ocr-pipeline-self-test").apply { mkdirs() }
        val file=AtomicFile(File(directory,"report.json")); val stream=file.startWrite()
        try { stream.write(report.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch(error: Exception) { file.failWrite(stream); throw error }
    }
    private val FIXTURES=listOf("明天下午3点提交实验报告","地点信息楼302","周五18点30分前完成报名")
}
