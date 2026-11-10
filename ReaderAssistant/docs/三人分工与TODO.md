# 三人分工与 TODO

日期：2026-11-08。A、B、C 分别对应成员一、成员二、成员三。C 的工作相对简单。任务编号写在源码 TODO 中，可全局检索。

## A：感知与本地 AI

| 编号 | 交付任务 | 主要文件 | 验收要求 |
| --- | --- | --- | --- |
| A-01 | QQ 内容适配 | capture/QqAccessibilityReader.kt | 保留消息/引用/时间关系，明确截断；不猜本人昵称或源日期 |
| A-02 | 微信聊天与公众号 OCR spike 和接入 | capture/ocr/LocalOcrAdapter.kt | 分场景真机记录可捕获性、授权停止、中文准确率、时延和内存；首次可离线 |
| A-03 | 本地模型选型验证 | inference/LocalSemanticEngine.kt | 同一测试集报告任务/非任务/过去事件/归属/关联/取消/冲突效果及资源成本 |
| A-04 | 模型运行适配器 | inference/LocalSemanticEngine.kt | 资源随包，结构化输出，取消/超时可处理；无云端或首次下载依赖 |
| A-05 | 持续采集生命周期、频率和背压 | capture/CaptureBus.kt、服务 | 不依赖主界面显示；有界积压；丢页可见；暂停无新结果入库；后台可靠性单独测 |
| A-06 | 返回桌面检测、识别状态通知 | capture/DesktopDetector.kt | 只把默认桌面算退出，不读其他应用正文；通知栏暂停恢复调用业务接口 |
| A-07 | 任务检索、预处理、关联建议 | inference/InferencePipeline.kt | B 给数据接口，A 做排序/上下文；不因缺时间或取消词丢核心信息，不强行匹配 |
| A-08 | 时间/身份/证据校验 | inference/SourceAnchoredTimeInterpreter.kt | 来源时间锚定，日期精度保留，未知来源标记，错误编号/字段不入业务层 |

A 不修改任务库，不决定是否自动接受正式变化。已有微信无障碍失败经验作为当前实现前提；不能扩大成任意设备、版本和页面均失败的证明。

## B：业务、数据、提醒与集成负责人

| 编号 | 交付任务 | 主要文件 | 验收要求 |
| --- | --- | --- | --- |
| B-01 | 统一任务库、事务与版本 | data/local/、RoomTaskRepository.kt | 已接创建/确认/完成基础；补迁移、查询、并发测试；确认前后 ID 不变 |
| B-02 | 持久去重、关联、候选合并/拆分 | business/AnalysisCommitter.kt、仓库 | 重启重复来源不新增；跨来源关联不只比标题；多来源及字段证据可拆开 |
| B-03 | 正式补充/改期/取消/冲突提案 | 同上、ProposalRow | 确认前原任务和提醒不变；冲突无预选；变更流从空占位改为数据库流 |
| B-04 | 超时延期恢复 | 同上 | 明确未来延期才提案；确认原 ID 恢复；完成任务不自动恢复；旧清理不删新证据 |
| B-05 | 提醒 outbox、调度/取消/对账 | reminder/ | 统一 PendingIntent 身份；权限降级真实可见；失败不标成功；数据库与调度可核对 |
| B-06 | 三种提示模式、回执、桌面汇总 | AssistantCoordinator.kt | 正式变化立即卡片；卡片收起仍在队列；重复不弹；结合 A-06 返回桌面才汇总 |
| B-07 | 通知完成/稍后、旧广播门禁 | reminder/ReminderReceiver.kt | revision/generation 校验；稍后不改任务时间；完成旧动作失效；goAsync finally finish |
| B-08 | 自动超时、详细来源/历史/缓存清理 | business/RetentionCoordinator.kt | 日期到次日开始超时；保留摘要/去重；共享来源权限隔离；活跃任务不受7天删除 |
| B-09 | 清空本地数据与隐私检查 | 同上、Manifest | 先失效采集/推理，再取消提醒清库/图片/缓存；无备份/原文日志泄漏 |
| B-10 | 平台设置适配与装配维护 | data/LocalSettingsStore.kt、bootstrap/ | 先用已运行接口；按需迁 DataStore/Hilt，保持唯一状态源和作用域 |

B 负责 contract/ 的统一版本、联调样例、合并冲突与完整流程验收。需要 A/C 配合的能力由 B 发起接口协调，而不是让 C 自行补业务逻辑。

## C：界面与交互，较简单

| 编号 | 交付任务 | 主要文件 | 验收要求 |
| --- | --- | --- | --- |
| C-01 | 今日概览和基础组件 | presentation/screens/HomeScreen.kt、ScreenComponents.kt | 空/加载/受限状态清楚，样例与真实识别区分 |
| C-02 | 待确认列表及卡片内容 | PendingScreen.kt、PromptCardRenderer.kt | 摘要/时间/实际提醒/不确定项可见，确认/修改调用 B 接口 |
| C-03 | 正式待办列表 | TaskListScreen.kt | 使用同一数据流，完成/超时/无提醒显示准确 |
| C-04 | 任务详情、编辑、来源及差异 | TaskDetailScreen.kt | 表单校验/冲突选择/来源展示；处理 STALE，不能覆盖新版 |
| C-05 | 设置和授权引导页面 | SettingsScreen.kt | 三种模式、24小时/7天/30分钟/10分钟可改；平台检测/跳转由 A/B 提供 |
| C-06 | 正式导航、视觉及 Compose 迁移 | MainActivity.kt、AssistantViewModel.kt | 不在 Activity 推理/轮询；可先保留 Views 完成联调，再迁 Compose |

C 不负责 OCR、模型推理、数据库、合并算法、AlarmManager、后台存活、跨应用窗口权限或卡片投递机制。C 做卡片外观；B 做提示策略和投递，A 配合平台能力验证。

## 建议交付顺序

1. 共同冻结 contract/ 与样例；B 跑样例保存/确认，C 用样例搭页面，A 同时验证 OCR 和模型。
2. B 完成状态与提醒，A 接 QQ 真实内容和模型；C 接真实 Flow，不等待最终视觉或 OCR 全部完成。
3. 三人联调任务关联、正式变更、恢复、卡片和桌面汇总，再接通过验证的微信路线。
4. B 汇总测试；A 负责读取/模型资源报告，C 负责交互检查。未通过项明确记录，不拿模拟样例替代。
