package com.example.localtaskassistant.capture.ocr

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.*
import android.os.SystemClock
import android.util.AtomicFile
import com.example.localtaskassistant.contract.OcrFrame
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.sqrt

/** 测试专用：读取ADB导入的固定私有PNG；用户已批准查看截图和实际识字输出。 */
internal object OcrDebugImageTest {
    suspend fun run(context: Context,chatDarkText: Boolean=false): JSONObject=withContext(Dispatchers.Default) {
        check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val report=JSONObject().put("runId","A022g_"+UUID.randomUUID().toString().replace("-",""))
            .put("phase","RUNNING").put("source","AUTHORIZED_DEBUG_PNG")
            .put("binaryMode",if(chatDarkText) "CHAT_DARK_TEXT_160" else "WHITE_PAGE_200")
        val variants=JSONArray(); report.put("variants",variants); publish(context,report)
        val began=SystemClock.elapsedRealtime(); var frame: OcrFrame?=null
        try {
            val file=File(context.filesDir,"ocr-debug-import/input.png")
            require(file.length() in 1..20_000_000)
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(file.path,bounds)
            require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=16_777_216)
            val decoded=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inPreferredConfig=Bitmap.Config.ARGB_8888 })
                ?: error("测试PNG解码失败")
            val factor=minOf(1.0,2560.0/maxOf(decoded.width,decoded.height),
                sqrt(OcrRgbaPixels.MAX_PIXELS.toDouble()/(decoded.width.toDouble()*decoded.height)))
            val width=maxOf(1,(decoded.width*factor).toInt()); val height=maxOf(1,(decoded.height*factor).toInt())
            report.put("originalWidth",decoded.width).put("originalHeight",decoded.height)
                .put("width",width).put("height",height)
            val image=if(width==decoded.width && height==decoded.height) decoded else
                Bitmap.createScaledBitmap(decoded,width,height,true).also { decoded.recycle() }
            val bytes=ByteArray(width*height*4); val row=IntArray(width)
            try {
                for(y in 0 until height) {
                    image.getPixels(row,0,width,0,y,width,1)
                    for(x in 0 until width) {
                        val at=(y*width+x)*4; val c=row[x]
                        bytes[at]=(c ushr 16).toByte(); bytes[at+1]=(c ushr 8).toByte()
                        bytes[at+2]=c.toByte(); bytes[at+3]=(c ushr 24).toByte()
                    }
                }
            } catch(error: Exception) { bytes.fill(0); throw error }
            finally { row.fill(0); image.recycle() }
            frame=OcrFrame(bytes,width,height)
            for((mode,binary,tiled) in listOf(Triple("FULL_RAW",false,false),Triple("FULL_BINARY",true,false),
                Triple("BANDS_RAW",false,true),Triple("BANDS_BINARY",true,true))) {
                var text=""
                val measure=OcrBandRecognition.measure(frame,binary,tiled,chatDarkText=chatDarkText,onText={ text=it })
                variants.put(JSONObject().put("mode",mode).put("hits",measure.hits).put("characters",measure.characters)
                    .put("bands",measure.bands).put("elapsedMillis",measure.elapsedMillis).put("failed",measure.failed)
                    .put("text",text))
                publish(context,report)
                if(measure.failed) error("测试OCR未完成")
            }
            report.put("phase","COMPLETE")
        } catch(cancelled: CancellationException) { report.put("phase","CANCELLED"); throw cancelled }
        catch(_: Exception) { report.put("phase","ERROR") }
        finally { frame?.bytes?.fill(0); report.put("elapsedMillis",SystemClock.elapsedRealtime()-began); publish(context,report) }
        report
    }
    private fun publish(context: Context,report: JSONObject) {
        val directory=File(context.filesDir,"ocr-debug-import").apply { mkdirs() }
        val file=AtomicFile(File(directory,"report.json")); val stream=file.startWrite()
        try { stream.write(report.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch(error: Exception) { file.failWrite(stream); throw error }
    }
}
