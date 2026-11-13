package com.example.localtaskassistant.capture.ocr

/** 单帧预览使用单调时钟；不可覆盖、跨会话或超期领取。调用方负责同步和像素释放。 */
internal class PreviewLease {
    private var runId: String?=null
    private var ready=false
    var expiresAt: Long=0
        private set
    fun arm(id: String, now: Long) { require(now>=0); runId=id; ready=false; expiresAt=now+60_000 }
    fun canPublish(id: String, now: Long) = runId==id && !ready && now<expiresAt
    fun publish(id: String, now: Long): Boolean {
        if(!canPublish(id,now)) return false
        ready=true; return true
    }
    fun canTake(now: Long)=runId!=null && ready && now<expiresAt
    fun take(now: Long): Boolean {
        if(!canTake(now)) return false
        clear(); return true
    }
    fun clear() { runId=null; ready=false; expiresAt=0 }
}
