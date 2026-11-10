package com.example.localtaskassistant.capture.ocr

import com.example.localtaskassistant.contract.*

/** 成员一 A-02：先真机 spike，再选择随包离线 OCR 实现。当前不会截图。 */
class LocalOcrAdapter : TextRecognizer {
    override suspend fun recognize(frame: OcrFrame): Result<List<TextBlock>> {
        // TODO(A-02): MediaProjection 授权/停止生命周期、内存帧、OCR 与资源释放。
        // TODO(A-02): 微信聊天/公众号分别验证；受保护画面不能承诺可读。
        // 不自动下载模型、不上传画面、不用 isAccessibilityTool 绕过限制。
        return Result.failure(IllegalStateException("微信本地 OCR 尚未实现"))
    }
}
