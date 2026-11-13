# JDK17构建环境修复

日期：2026-11-11。状态：修复完成，验证通过。

## 明确授权及范围

用户在知道此项属于共用Gradle配置后，明确要求“修复”。本次仅作为这一项修复的例外，修改`gradle/gradle-daemon-jvm.properties`，不扩大A的常规白名单。

不修改业务源码、公共contract、B/C代码、测试、Kotlin/KSP/AGP版本、Manifest、Gradle构建脚本或local.properties。测试记录仅放A目录。

## 原因和实际修复

用户报错在KSP/Kotlin内部的JavaVersion.parse解析`25.0.4`时发生。当前Daemon JVM criteria指定`toolchainVersion=25`，优先于JAVA_HOME及org.gradle.java.home。

改用17，并同步下载URL映射。候选配置来自同机原ReaderTest已使用的JDK17 criteria文件；不是仅修改版本号而保留25的URL。本机现有JDK17路径为`C:/Users/lzq/.jdks/ms-17.0.20.1`。

配置旧内容与差异保存在工作区`work/jdk17-fix/`，应用前核对SHA-256，防止覆盖并发改动。

## 验证要求

1. 查看Gradle实际选择的JVM，不能只凭JAVA_HOME判断。
2. 强制重新运行Gradle单元测试，确保KSP和Kotlin编译重新执行。
3. 预期NodeTextCollectorTest为29项，连同15项回归共44项，0失败。
4. 构建主APK，不安装到手机。
5. 比较本轮开始的全app/src哈希及非A文件清单，只允许上述一个授权配置变化。

不使用build-local.ps1，避免它重写local.properties；直接运行Gradle，通过进程环境设置本机JDK和构建临时目录。

本次修复不代表QQ真机、OCR或本地模型验证通过，也不进入下一部分。

参考：[Gradle Daemon JVM criteria](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:daemon_jvm_criteria)。


## 实际验证结果

- Gradle 9.6.0显示Daemon JVM criteria为Java17。
- `--info`日志确认后台进程和测试执行器均使用本机Microsoft JDK17.0.20.1。
- 离线执行 `:app:testDebugUnitTest :app:assembleDebug --rerun-tasks`，42个任务实际重新执行，BUILD SUCCESSFUL。
- NodeTextCollectorTest：29项通过；连同原有15项回归共44项，0失败/错误。XML报告时间晚于本轮开始，未使用旧报告充当结果。
- KSP、Kotlin编译及APK构建完成，没有再次出现IllegalArgumentException:25.0.4。
- 本轮未重新运行Lint或手机测试，不声称这些项目有新验收结果。
- SHA-256范围核对：非A文件仅授权的`gradle/gradle-daemon-jvm.properties`改变；全app/src（包括A/B/C及测试、Manifest、资源）未变。依赖和local.properties也未变。

完整构建日志、差异和原配置备份在工作区`work/jdk17-fix/`。本修复不更改A的常规白名单；下一部分开发仍须先拍新基线再检查。

## 用户在Android Studio复测

重新同步当前内层工程，再运行NodeTextCollectorTest，预期29项绿色。若仍显示25，先查看JVM criteria选择是否被IDE重新改回25，保留新报错再排查；不要升级Kotlin/KSP或改业务代码绕过。
