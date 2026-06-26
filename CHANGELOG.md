# Changelog

本文件记录面向发版的简要变更摘要。详细资产、校验和、SBOM、许可证和
attestation 信息以 release manifest 和 GitHub Release 为准。

## Unreleased

用于记录下一个版本计划发布的用户可见变化。

### Added

- 构建时自动检测 CloudSim Plus 本地 JAR 是否缺失，若缺失则通过子构建自动编译源码，无需手动执行 `sanitizeCloudSimPlusJarManifest`。
- 新增 Gradle 依赖与插件中国镜像源配置（阿里云优先 → 官方回退），提升国内网络环境下的首次构建速度。
- 新增 `docs/troubleshooting.md` 章节：Maven 安装指南（Windows/macOS/Linux）和首次构建 5 步流程。

### Changed

- CloudSim Plus 本地仓库声明从 `exclusiveContent` 改为 `mavenContent`，允许依赖解析在本地 JAR 缺失时回退到镜像源，避免配置阶段直接失败。
- KtLint 格式化 `build.gradle.kts` 新增代码段（trailing comma、chain method continuation）。

### Fixed

- 修复新环境中直接运行 `build` 因 CloudSim Plus 未编译而失败的问题（`Could not find org.cloudsimplus:cloudsimplus:X.Y.Z`）。

## 1.2.1 - 2026-06-23

### Added

- 新增 realtime 基线算法：`EDF_REALTIME`、`LLF_REALTIME`、`EFT_REALTIME`、`SRPT_REALTIME` 和 `PRIORITY_DEADLINE_REALTIME`。
- 新增 realtime 专用候选评分模型，并输出 `realtime_candidate_scores.csv` 用于解释 VM 候选分数。
- 新增 deadline admission，可按 soft、firm、hard deadline 和 accept、reject、degrade、retry_later 策略处理 deadline miss。
- 新增周期性重调度，支持 pending、waiting、running 任务的 deadline/score 策略重评估。
- 新增 periodic、sporadic、diurnal burst、mixed short/long、DAG chain/layered 等 realtime workload，并补充 DAG 依赖强约束。
- 新增 host 级资源和拓扑模型增强，包括 CPU overcommit、带宽共享、storage IOPS、镜像拉取队列和 noisy-neighbor pressure。
- 新增 enhanced autoscaling，支持 cooldown、warm pool、scale-in drain、min active VM、arrival-rate 预测和 deadline slack pressure。
- 新增 realtime 事件级观测输出 `realtime_events.csv`，可复盘 arrival、selection、submission、completion、failure、preemption、reschedule 和 autoscaling 事件。
- 新增 realtime workload、resource topology、autoscaling advanced、event observation 示例配置。
- 补充项目 TODO 记录，便于后续版本规划。

### Changed

- realtime `ALL` 和默认算法展开包含新增 realtime baseline。
- `PSO_REALTIME` 和 `WOA_REALTIME` 默认使用 realtime score objective 作为优化目标。
- dry-run 文本和 JSON 输出补充 deadline admission、rescheduling、workload、resource topology、autoscaling 和 event observation 配置。
- realtime trial/summary CSV schema 扩展 dependency、deadline、rescheduling、resource topology、autoscaling、score 等聚合指标。
- 改进 README 项目概览和配置文件中文注释，降低新用户理解成本。
- 明确容器镜像 tag 与 digest 用法，避免误用 GHCR attestation fallback tag。
- 将项目版本更新为 `1.2.1`，同步容器镜像拉取示例。

### Fixed

- 修正 realtime autoscaling 周期 tick 在空队列或仅运行任务场景下可能持续扩容/续约的问题。
- 修正 rescheduling 失败尝试不消耗上限导致周期 tick 持续重试的问题。

## 1.2.0 - 2026-06-22

### Added

- 增加 release package、release manifest、SBOM、许可证报告和容器发布链路。
- 增加 GitHub Actions release、container attestation 和供应链检查流程。

### Changed

- 拆分 batch/realtime scheduler 源码布局，并保留兼容别名。
- 改进 Windows/Unix 运行脚本对版本化 fatJar 的解析。

### Fixed

- 修复 v1.2.0 release 检查、actionlint 路径和 GHCR 凭据相关问题。

## 1.1.0 - 2026-01-11

历史版本。后续如需要回溯发布说明，可从对应 tag 和 GitHub Release 补充。

## 1.0.0 - 2025-12-23

首个版本。后续新版本从 `Unreleased` 区域整理到对应版本号下。
