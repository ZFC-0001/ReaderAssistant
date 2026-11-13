# A-01.2a：QQ真机节点适配器可读性测试

日期：2026-11-11。状态：服务内QQ固定消息可读性验证通过；首次Instrumentation失败记录保留且旧用例停用。

## 范围

当前只修改A自己的capture目录、对应测试及A记录。新方法增加FixtureReadback/DebugQqReadback及服务内调试钩子；不改公共接口、B/C、Manifest、Gradle配置或依赖。

本次验证：在真实QQ页面调用产品的QqAccessibilityReader，是否能完整读到三条合成测试消息，并在重复读取时仍可读到。

不验证：生产服务的事件时序/后台持续工作、消息分组、本人昵称、源日期、语义关联、AI或任务提醒。这些不能因本测试通过而一并宣称完成。

## 已核对的连接及许可

真实手机Android15/API35，QQ和助手已安装；助手内capture=true、QQ在白名单；系统无障碍已授权，ActivityManager显示服务requested/received/hasBound为true。设备序列号不写入本报告。

## 用户准备

在自己控制的QQ测试会话（例如“我的电脑”）逐条发送：

```text
A01_TEST_1 明天下午3点提交实验报告
A01_TEST_2 地点信息楼302
A01_TEST_3 收到
```

保持手机解锁，三条消息同时显示，关闭键盘/菜单/安装弹窗，不切到其他聊天。消息由用户手动发送，测试不会替用户点击发送。

## 原Instrumentation执行方法（已停用，不再按本节运行）

实测普通Instrumentation启动会影响被测试应用的无障碍授权，故该用例明确标记Ignore；断言和失败记录保留。跳过不计通过，真实验收改用文末的服务内方式。

旧用例曾通过Android Studio/ADB启动，结果停在服务授权前置断言。此方法不再执行；即使重新手动授权，也不要重跑它。

## 隐私与技术边界

测试只在目标包名为QQ时遍历文字。原文仅在当前进程中用于查找固定测试串，输出只有计数和布尔值，不保存原文、截图或完整节点树。失败消息不带实际页面内容。

测试使用Instrumentation的UiAutomation连接，在测试进程中调用同一个产品读取适配器，并以FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES保持已有服务。它不是产品运行时采集路线，也不证明生产服务已经把同样内容送入业务流程。

来源：[Android UiAutomation说明](https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)。

## 结果

- 编译：已执行 :app:assembleDebugAndroidTest，BUILD SUCCESSFUL（46个任务，12执行、34最新）；测试类已进入设备测试APK。
- 首次真机测试：1项失败，停在“读屏服务已授权”前置断言，尚未读取QQ。
- A-01.2a固定消息可读性：本轮服务内验证通过，见文末证据。
- 整体A-01：尚未完成；本项不能代替消息结构、发送人、来源日期和引用解析验证。

## 首次失败与方法纠正

运行前已由ADB确认应用开关、QQ白名单及服务授权/绑定。启动Instrumentation后，Settings中本应用的服务授权消失，服务解绑。没有将此现象说成用户未授权，也未自动修改手机授权。

AOSP的startInstrumentation实现存在对目标应用forceStop的路径；无障碍服务的force-stop处理会移除启用记录，与本次现象一致。DONT_SUPPRESS标志不等于能阻止测试启动前的目标进程强停。

- [AOSP启动Instrumentation实现](https://android.googlesource.com/platform/frameworks/base/+/6435f9bbec9b/services/core/java/com/android/server/am/ActivityManagerService.java)
- [AOSP无障碍force-stop处理](https://android.googlesource.com/platform/frameworks/base/+/8310f87d96791e8b864df5e821fe65273c2dd874/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java)

原用例保持断言并明确Ignore，不作为通过证据。原始失败输出见A目录证据文件。后续不再对本应用运行这个Instrumentation来验证自身服务。

## 当前方法：服务自身计数

1. 仅debug构建且应用私有files/qq-probe/control.txt存在时开启；release不会记录。
2. 用户仍须手动开启QQ读屏、应用识别开关并选择QQ。会话失效/暂停后不继续写诊断。
3. 直接在生产服务调用QqAccessibilityReader后，检查固定三条合成消息。
4. 输出仅包含runId、采样数、块数、truncated、fixtureHits、采样时刻和passed，不输出消息/昵称/群名/完整节点树。
5. 后台队列仅容纳一个待处理诊断样本；不保存原文。连续两个已采样页面均3/3、未截断、块数合规才通过。
6. 新runId重置，旧报告不能算本次结果；control文件写入前的在途样本也不计入。
7. 不启动Instrumentation，不强停服务，不代用户修改无障碍授权；结束后移除控制文件即停止诊断。

## 本轮真机结果（2026-11-11）

- 环境：Android15/API35，QQ 9.2.5。只代表该手机与此QQ版本下的当前合成测试会话。
- 当前runId：A01_1c58ea9dfc07，与事先写入控制文件的本轮标记一致。
- 已采样4次，连续完整计数达到2；三条固定消息命中3/3；39个文字块；truncated=false；passed=true。
- 服务诊断：gate=qq_read_finished；policyEnabled、qqAllowed、connected、currentSession均为true；诊断工作队列为0。
- 已移除应用私有control.txt，停止临时验证；result.json只保留计数，不保存原文、截图、群名或昵称。无障碍授权仍保持，不修改用户的识别设置。
- 证据：`证据/A01-2a_QQ服务内计数通过.json`。首次Instrumentation失败证据继续保留，不将旧用例跳过算通过。

## 本轮问题与A范围修复

1. 首次检查时没有报告：授权、QQ白名单和绑定均存在；曾观察到QQ不在前台，服务按范围跳过。
2. 用户回到QQ后，旧版服务诊断多次超时；调试连接未取得方法栈，不能据此声称已证明具体卡点或根因。后来旧runId也出现成功记录，但没有将其算作新版本本轮结果。
3. 将读屏及轮询放入独立HandlerThread，主线程继续处理服务回调与诊断；增加不含聊天内容的固定步骤状态。
4. 节点遍历增加2秒单调时钟预算，超时标记truncated并停止继续读取。预算是协作式的：单次远端Android调用仍可能超时，不能承诺硬性2秒完成；活动根和窗口获取也不由节点遍历预算覆盖。
5. 暂停/服务断开后，读取完成的旧会话结果不再交付；服务销毁时清理工作线程和回调。
6. 修复后本轮服务诊断正常响应，并取得上述新runId结果。不能由此推断所有后台、锁屏、切换应用和高频事件场景已稳定。

## 编译、测试与文件范围

- 最终版本执行assembleDebug与testDebugUnitTest成功（42个任务，11执行、31最新）。JUnit XML统计64项，失败0、错误0、跳过0。
- 新增4项可控时钟测试覆盖慢节点超时、停止下一根、正常快节点和无效预算；之前60项继续通过。
- 旧设备用例仅编译过，未再次启动；64项均为JVM单元测试，设备通过证据单独如上。
- 主包使用手机原包一致的调试证书覆盖安装，签名前后APK有效载荷一致；没有卸载主包或清数据。先前临时测试包已清理。
- 生产改动仅在capture目录：夹具计数与私有调试开关、服务诊断/工作线程、节点时间预算；对应test/androidTest及本A文档/证据也在A范围。
- 未修改公共契约、B/C、Manifest、Gradle配置/依赖或Git暂存内容。非A文件以部署前哈希基线复核。

## 后续验收边界

本次只确认真实QQ页面能读到指定三条消息，并连续重复读到；不代表准确提取任务、本人识别、消息日期解析、跨应用关联、模型推理或提醒已完成。下一小步仍应在A-01内验证消息结构与上下文，不直接跳到OCR或模型接入。
