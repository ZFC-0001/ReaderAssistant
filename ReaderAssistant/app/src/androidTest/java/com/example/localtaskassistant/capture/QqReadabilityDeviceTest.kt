package com.example.localtaskassistant.capture

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.app.UiAutomation
import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Ignore
import org.junit.runner.RunWith

/**
 * A-01.2a：真机上调用产品的QQ读取适配器。不是后台连续读屏或AI功能验收。
 *
 * 用户先在自己的QQ测试会话中发送下方三条固定消息，并让它们同时可见。
 * 测试不点击、不发送消息、不截图、不导出界面树，不打印页面原文。
 * 普通JVM测试不能运行本类，须使用androidTest并明确选择真实手机。
 */
@RunWith(AndroidJUnit4::class)
@Ignore("本机实测Instrumentation会强停目标应用并移除其无障碍授权；失败记录保留，改用服务内qq-probe验证。本项不计通过。")
class QqReadabilityDeviceTest {
    @Test
    fun threeSyntheticMessagesAreReadableTwiceFromQQ() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("assistant-settings", Context.MODE_PRIVATE)
        assertTrue("先在助手设置开启识别并保存", preferences.getBoolean("capture", false))
        assertTrue("先在助手设置选中QQ并保存",
            preferences.getStringSet("apps", emptySet()).orEmpty().contains(QQ_PACKAGE))
        assertTrue("先手动开启本地事务助手的QQ读屏服务", assistantServiceEnabled(context))
        assertFalse("请先解锁手机并打开QQ测试会话",
            (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked)

        // 使用测试专用框架连接读取同一适配器；保持已有无障碍服务，不自动改变用户授权。
        val automation = requireNotNull(instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)) {
            "无法建立设备测试读取连接"
        }
        val info = requireNotNull(automation.serviceInfo) { "设备测试读取连接尚未就绪" }
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        automation.serviceInfo = info

        val reader = QqAccessibilityReader()
        val first = reader.read(awaitQQRoots(automation))
        assertReadableFixtures(first, "first")

        // 再取一次当前根，验证重复读取不会因上次的临时节点去重而清空。
        SystemClock.sleep(250)
        val second = reader.read(awaitQQRoots(automation))
        assertReadableFixtures(second, "second")
        assertTrue("测试后读屏服务应仍保持用户授权", assistantServiceEnabled(context))
    }

    private fun assistantServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val service = it.resolveInfo.serviceInfo
            service.packageName == context.packageName &&
                service.name == "${context.packageName}.capture.AccessibilityCaptureService"
        }
    }

    private fun awaitQQRoots(automation: UiAutomation): List<AccessibilityNodeInfo> {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = automation.rootInActiveWindow
            // 其他应用只检查根包名，不遍历正文。键盘/安装弹窗挡住时等待用户回QQ。
            if (root?.packageName?.toString() == QQ_PACKAGE) {
                val roots = mutableListOf(root)
                automation.windows.orEmpty().forEach { window ->
                    val other = window.root
                    if (other != null && other.packageName?.toString() == QQ_PACKAGE && other != root)
                        roots.add(other)
                }
                return roots
            }
            SystemClock.sleep(200)
        }
        throw AssertionError("当前前台不是QQ测试会话；请关闭弹窗、回到显示三条测试消息的QQ页面后重试")
    }

    private fun assertReadableFixtures(page: ReadPage, sample: String) {
        val matches = FIXTURES.map { expected -> page.blocks.any { it.text.contains(expected) } }
        // 仅打印计数/布尔值；断言失败也不输出实际聊天、昵称、文章或整棵节点树。
        println("A01.2 sample=$sample blocks=${page.blocks.size} truncated=${page.truncated} fixtureHits=${matches.count { it }}/3")
        assertFalse("本次页面被标为不完整；先分析读取限制，不进入下一部分", page.truncated)
        assertTrue("QQ没有暴露可读文字节点", page.blocks.isNotEmpty())
        assertTrue("读取超过A-01.1约定的250块上限", page.blocks.size <= 250)
        matches.forEachIndexed { index, found ->
            assertTrue("没有读到测试消息编号${index + 1}；确认三条消息同时可见且文字完全一致", found)
        }
    }

    private companion object {
        const val QQ_PACKAGE = "com.tencent.mobileqq"
        val FIXTURES = listOf(
            "A01_TEST_1 明天下午3点提交实验报告",
            "A01_TEST_2 地点信息楼302",
            "A01_TEST_3 收到",
        )
    }
}
