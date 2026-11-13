package com.example.localtaskassistant.capture.ocr

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** 用户已授权的测试专用导出。默认关闭，不进入常规日志/业务库/版本库。 */
internal object DebugFrameExport {
    fun write(context: Context,runId: String,image: Bitmap,rgba: ByteArray,width: Int,height: Int) {
        check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val directory=File(context.filesDir,"wechat-debug-export").apply { mkdirs() }
        fun save(name: String,writer: (java.io.OutputStream)->Unit) {
            val file=AtomicFile(File(directory,name)); val stream=file.startWrite()
            try { writer(stream); file.finishWrite(stream) }
            catch(error: Exception) { file.failWrite(stream); throw error }
        }
        save("input.rgba") { it.write(rgba) }
        save("input.png") { check(image.compress(Bitmap.CompressFormat.PNG,100,it)) }
        val sha=MessageDigest.getInstance("SHA-256").digest(rgba).joinToString("") { "%02x".format(it) }
        val meta=JSONObject().put("runId",runId).put("width",width).put("height",height)
            .put("format","TIGHT_RGBA8888").put("rgbaSha256",sha)
        save("meta.json") { it.write(meta.toString().toByteArray(Charsets.UTF_8)) }
    }
}
