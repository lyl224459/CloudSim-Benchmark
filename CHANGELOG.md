# Changelog

本文件记录面向发版的简要变更摘要。详细资产、校验和、SBOM、许可证和
attestation 信息以 release manifest 和 GitHub Release 为准。

## Unreleased

用于记录下一个版本计划发布的用户可见变化。

### Added

- 补充项目发版记录入口，便于后续版本整理发布说明。

### Changed

- 改进 README 项目概览和配置文件中文注释，降低新用户理解成本。
- 明确容器镜像 tag 与 digest 用法，避免误用 GHCR attestation fallback tag。

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
