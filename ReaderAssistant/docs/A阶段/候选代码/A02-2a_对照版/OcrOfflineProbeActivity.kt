package com.example.localtaskassistant.ocrprobe

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.SystemClock
import android.net.ConnectivityManager
import android.provider.Settings
import android.util.AtomicFile
import android.widget.*
import com.example.localtaskassistant.capture.ocr.LocalOcrAdapter
import com.example.localtaskassistant.contract.OcrFrame
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** 独立技术spike：只识别自绘合成文字，不访问微信、通知、相册或无障碍。 */
class OcrOfflineProbeActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var output: TextView
    private lateinit var start: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(padding,padding,padding,padding)
        }
        column.addView(TextView(this).apply { textSize=23f; text="中文OCR · 离线自测" })
        column.addView(TextView(this).apply {
            text="这是独立开发测试，不读取微信。\n\n首次点击前，请开启飞行模式并关闭Wi-Fi。模型已随安装包携带，测试图只在内存中生成。\n\n测试内容：\n"+FIXTURES.joinToString("\n")
        })
        start = Button(this).apply { text="运行原图/黑白增强对照"; setOnClickListener { runTest() } }
        column.addView(start)
        output = TextView(this).apply { textSize=17f; text="尚未运行。"; setTextIsSelectable(true) }
        column.addView(output)
        setContentView(ScrollView(this).apply { addView(column) })
    }
    private fun runTest() {
        val offline = getSystemService(ConnectivityManager::class.java).activeNetwork==null
        val airplaneEnabled = Settings.Global.getInt(contentResolver,Settings.Global.AIRPLANE_MODE_ON,0)==1
        if (!airplaneEnabled || !offline) {
            output.text="请先开启飞行模式并关闭Wi-Fi，再开始。尚未调用识字模型。"
            return
        }
        start.isEnabled=false
        scope.launch {
            val runId="A022a_"+UUID.randomUUID().toString().replace("-", "")
            val began=SystemClock.elapsedRealtime()
            val airplane = Settings.Global.getInt(contentResolver,Settings.Global.AIRPLANE_MODE_ON,0)==1
            val internet = checkSelfPermission(Manifest.permission.INTERNET)==PackageManager.PERMISSION_GRANTED
            output.text="正在本地识字，请稍候……"
            writeReport(JSONObject().put("runId",runId).put("phase","RUNNING")
                .put("airplaneModeOnAtStart",airplane).put("noActiveNetworkAtStart",offline)
                .put("internetPermissionGranted",internet))
            var raw: ByteArray?=null
            try {
                val frame=withContext(Dispatchers.Default) { createFixtureFrame() }
                raw=frame.bytes
                val variants=JSONArray()
                val display=mutableListOf<String>()
                var binaryPassed=false
                for ((mode,enhance) in listOf("RAW" to false,"WHITE_PAGE_BINARY" to true)) {
                    val adapter=LocalOcrAdapter(whitePageBinary=enhance)
                    val variantStarted=SystemClock.elapsedRealtime()
                    val result=try { adapter.recognize(frame) } finally { adapter.close() }
                    val lines=result.getOrNull().orEmpty()
                    val text=lines.joinToString("\n") { it.text }
                    val compact=text.replace(Regex("\\s+"),"")
                    val hits=FIXTURES.count { compact.contains(it) }
                    val phase=if(result.isFailure) "ERROR" else if(hits==FIXTURES.size) "PASS" else "FAIL"
                    variants.put(JSONObject().put("mode",mode).put("phase",phase).put("fixtureHits",hits)
                        .put("lineCount",lines.size).put("characterCount",text.length)
                        .put("elapsedMillis",SystemClock.elapsedRealtime()-variantStarted))
                    if(enhance) binaryPassed=phase=="PASS"
                    display.add("${if(enhance) "黑白增强" else "原图"}：$hits/${FIXTURES.size} ($phase)\n$text")
                    if(result.isFailure) break // 未确认SDK任务结束时不继续发起下一项。
                }
                raw.fill(0)
                val cleared=raw.all { it==0.toByte() }
                val elapsed=SystemClock.elapsedRealtime()-began
                writeReport(JSONObject().put("runId",runId).put("phase","COMPARISON_COMPLETE")
                    .put("experimentVersion",2).put("fixtureVersion",1).put("fixtureTotal",FIXTURES.size)
                    .put("variants",variants).put("binaryCandidatePassed",binaryPassed)
                    .put("comparison","whitespace_removed_exact_substrings")
                    .put("elapsedMillis",elapsed).put("inputBytesCleared",cleared)
                    .put("airplaneModeOnAtStart",airplane).put("noActiveNetworkAtStart",offline)
                    .put("noActiveNetworkAtEnd",getSystemService(ConnectivityManager::class.java).activeNetwork==null)
                    .put("internetPermissionGranted",internet))
                output.text="对照完成，总耗时${elapsed}毫秒\n飞行模式：${if(airplane) "开启" else "未开启"}\n"+
                    "黑白增强是否完整命中：${if(binaryPassed) "是" else "否"}\n\n"+
                    display.joinToString("\n\n")+"\n\n只比较同一合成图，不代表微信真实画面效果。结果文字可长按选择复制，不保存到文件。"
            } catch (cancelled: CancellationException) {
                raw?.fill(0)
                writeReport(JSONObject().put("runId",runId).put("phase","CANCELLED"))
                throw cancelled
            } catch (_: Exception) {
                output.text="自测异常，未通过。未保存输入图片或识字内容。"
                writeReport(JSONObject().put("runId",runId).put("phase","ERROR"))
            } finally {
                raw?.fill(0)
                if(!isDestroyed) start.isEnabled=true
            }
        }
    }
    private fun createFixtureFrame(): OcrFrame {
        val width=1400
        val height=480
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val bytes=ByteArray(width*height*4)
        val row=IntArray(width)
        try {
            val canvas=Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK; textSize=52f; typeface=Typeface.DEFAULT }
            FIXTURES.forEachIndexed { index,line -> canvas.drawText(line,40f,100f+index*140f,paint) }
            for(y in 0 until height) {
                bitmap.getPixels(row,0,width,0,y,width,1)
                for(x in 0 until width) {
                    val at=(y*width+x)*4
                    val c=row[x]
                    bytes[at]=(c ushr 16).toByte()
                    bytes[at+1]=(c ushr 8).toByte()
                    bytes[at+2]=c.toByte()
                    bytes[at+3]=(c ushr 24).toByte()
                }
            }
            return OcrFrame(bytes,width,height)
        } catch(error: Exception) { bytes.fill(0); throw error }
        finally { row.fill(0); bitmap.recycle() }
    }
    private fun writeReport(value: JSONObject) {
        val directory=File(filesDir,"ocr-self-test").apply { mkdirs() }
        val file=AtomicFile(File(directory,"report.json"))
        val stream=file.startWrite()
        try { stream.write(value.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch(error: Exception) { file.failWrite(stream); throw error }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private companion object {
        val FIXTURES=listOf("明天下午3点提交实验报告","地点信息楼302","周五18点30分前完成报名")
    }
}
