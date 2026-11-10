# Android 本地事务助手 · 三人协作开发骨架

本工程依据已确认功能文档及三人分工建立。目标是让三个人在明确的接口上填实现，不代表完整产品已完成。

**先看：[三人分工与 TODO](docs/三人分工与TODO.md) → [接口约定](docs/接口约定.md) → [架构与联调](docs/架构与联调.md)。**

## 三个人从哪里开始

| 成员 | 自己负责的目录 | 第一个入口 |
| --- | --- | --- |
| 一 / A：感知与本地 AI | `capture/`、`inference/` | `capture/AccessibilityCaptureService.kt`、`inference/LocalSemanticEngine.kt` |
| 二 / B：业务、数据、提醒、集成 | `business/`、`data/`、`reminder/`、`bootstrap/` | `business/AssistantCoordinator.kt`、`data/RoomTaskRepository.kt` |
| 三 / C：较简单的界面工作 | `presentation/` | `presentation/MainActivity.kt`、`presentation/screens/` |

Kotlin 源码根目录：`app/src/main/java/com/example/localtaskassistant/`。`contract/` 是共同协议，B 维护，三人确认后修改。

## 已搭好的内容

- 独立 Android app，应用 ID 为 `com.example.localtaskassistant`，不会覆盖原 ReaderTest 的安装和数据库。
- 公共模型/接口、应用级协调器、构造器注入、Room 九类表和已导出的 schema 入口。
- 手动联调样例 → 持久待确认 → 确认原 ID → 正式待办 → 完成和部分来源清理。
- 来源＋任务片段的持久去重、版本检查、来源7天策略、时间精度及提示路由的纯函数。
- QQ 基础无障碍采集：范围先校验，600ms 延迟、1秒节流、2.5秒轮询、有界队列；节点顺序保留。
- 暂停/撤销使旧采集 session 失效；分析提交与设置变更串行，避免暂停后旧结果继续提交。
- 基础页面、ViewModel、Flow 订阅与明确的未实现状态。没有界面轮询推理。

## 必须知道的未完成项

**没有接入小模型，没有微信 OCR，没有实际系统提醒，没有跨应用提示卡/通知栏控制/桌面汇总。跨来源关联、正式变更、延期恢复等是核心 TODO，并非可选功能。**

模型和提醒适配器返回“未接入/受限”，不会返回假成功。首页“接口联调样例”是手工数据，不能当作 AI 效果。待确认变更流当前为空占位。自动超时维护、完整清理、全部编辑操作仍须实现。

## 构建与运行

Android Studio 打开本目录，Gradle JVM 选择 JDK 17。SDK/AGP/Kotlin/Room 等沿用原项目版本，不在本次同时升级构建链。

```powershell
./scripts/build-local.ps1 -Offline
# 其他电脑：传入本机路径；无离线依赖缓存时去掉 -Offline
./scripts/build-local.ps1 -JdkPath '你的JDK17目录' -SdkPath '你的AndroidSDK目录'
```

`local.properties` 是本机文件，已忽略；不要提交个人路径/密钥。运行后先点“载入接口联调样例”，在待确认里确认，再从待办查看相同编号并完成。

基础采集需先在应用设置选中 QQ、开启识别并保存，再由用户在系统设置开启本应用无障碍服务。采集到内容后因模型未接入不会自动生成任务，这是正确的占位行为。默认关闭采集。

当前前端先使用可离线构建的 Views 占位；C 可按既定方向迁移 Compose。依赖注入为构造器注入，设置暂用私有 SharedPreferences；Hilt/DataStore 分别保留 B-10 迁移入口，不能把它们说成已接入。这样避免在框架阶段引入尚未下载和验证的依赖。

## 测试

- JVM：`./gradlew.bat :app:testDebugUnitTest`。
- 设备事务测试：`./gradlew.bat :app:connectedDebugAndroidTest`，必须连接真机或模拟器。
- 边界检查：`python scripts/check-boundaries.py`。
- 实际执行结果见 [验证记录](docs/验证记录.md)；编译通过不等于真机读屏、AI 或后台提醒通过。

原 ReaderTest 参考快照在 `reference/ReaderTest/`，不参与编译。原始项目未修改。
