# A成员文件范围约定

日期：2026-11-11。依据：用户明确要求“只改自己范围内的文件”。后续A工作按此执行；公共文档中的一般更新要求不覆盖本条用户指令。

## 允许人工修改

- `app/src/main/java/com/example/localtaskassistant/capture/`
- `app/src/main/java/com/example/localtaskassistant/inference/`
- `app/src/test/java/com/example/localtaskassistant/capture/`
- `app/src/test/java/com/example/localtaskassistant/inference/`
- `app/src/androidTest/java/com/example/localtaskassistant/capture/`
- `app/src/androidTest/java/com/example/localtaskassistant/inference/`
- `docs/A阶段/`：A的测试记录、范围基线和给B的接口需求。

这是当前白名单。模型资源、采集配置等如需新目录或共享文件，先交B确认，不自行扩大。

## 默认不修改

`contract/`、`business/`、`data/`、`reminder/`、`bootstrap/`、`presentation/`及B/C测试，Manifest、res配置、Gradle文件/版本/依赖、schema、AGENTS.md、公共文档和本机local.properties。

例如需要OCR依赖、服务注册、公共字段或模型资源目录时，A在自己文档目录写具体请求，交B的GPT处理。测试需要读取公共类型或运行原有回归测试，不等于允许修改它们。

## 构建产物与环境

`app/build/`、根build、Gradle缓存、IDE状态、调试构建状态属于工具生成物，检查器单独忽略。不能借“构建产物”名义忽略业务代码、schema或公共配置。

原build-local.ps1会重写local.properties。后续A优先用已配置的环境直接运行Gradle任务，通过进程环境/命令参数设置JDK和SDK，避免人工改工程配置；检查器也会保护local.properties的内容。

## 每轮检查

在真正的工程根目录执行；文件名由任务编号和轮次区分，不覆盖旧基线：

```powershell
python docs/A阶段/check_scope.py --snapshot docs/A阶段/范围基线/A01-2_开始.json
# 完成本轮A代码与测试后
python docs/A阶段/check_scope.py --check docs/A阶段/范围基线/A01-2_开始.json
```

检查器保护所有不在白名单内的非构建文件，检测修改、新增和删除。出现越界则退出失败，不自动删除/恢复任何人的文件。先核对变化是否来自其他成员，再处理本轮自己的越界；不能覆盖他人的工作。

这是核对工具，不是系统级写保护，也不能证明别的GPT一定遵守。A仍需在每次编辑前确认路径，并在每轮结尾报告实际修改文件。

## 本轮纠正

A-01.1的代码在A范围内。此前向公共`docs/验证记录.md`追加的A-01.1段落不属于A独占范围，单独撤回该段；测试结果完整保留在A自己的`A01-1_节点文字整理与测试.md`。

公共验证汇总今后由B处理。A不为了满足旧AGENTS中“更新公共验证记录”的一般要求而越过用户当前的明确分工边界。
