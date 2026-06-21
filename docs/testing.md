# Testing Guide

本文档说明测试结构、JUnit inventory、覆盖率门禁、TestKit、文档漂移和新增测试规则。
buildSrc task 设计和 TestKit 目标见 [build-logic.md](build-logic.md)。

## Test Layers

| Layer | Location | Purpose |
| :--- | :--- | :--- |
| Unit tests | `src/test/kotlin` | CLI、config、scheduler、broker、metrics、exporter 逻辑。 |
| Integration tests | `src/test/kotlin` | 小规模 CloudSim 真实路径、runner、broker 行为。 |
| buildSrc unit tests | `buildSrc/src/test/kotlin` | 纯 helper、parser、policy、sanitizer 逻辑。 |
| buildSrc TestKit tests | `buildSrc/src/test/kotlin` | Gradle task action、configuration cache、错误诊断。 |
| Documentation drift tests | `src/test/kotlin/config/DocumentationDriftTest.kt` | README TOML、docs/release-readiness、baseline 移除。 |

## JUnit Signature Gate

Kotlin expression-bodied test 容易返回 AssertJ 类型，导致 JUnit 不发现测试。项目用 `verifyJUnitTestSignatures` 阻断非 `Unit` test/template 方法。

正确：

```kotlin
@Test
fun `scenario`() {
    assertThat(result).isEqualTo(expected)
}
```

避免：

```kotlin
@Test
fun `scenario`() = assertThat(result).isEqualTo(expected)
```

签名门禁覆盖：

- `@Test`
- `@ParameterizedTest`
- `@RepeatedTest`
- meta-annotation / composed annotation
- `@TestTemplate`

`@TestFactory` 不要求返回 void。

## JUnit Inventory

测试入口清单：

```text
gradle/junit-test-inventory.lock
```

验证：

```powershell
.\gradlew.bat verifyJUnitTestInventory verifyJUnitTestSignatures --no-daemon --stacktrace
```

有意新增、删除或重命名测试后：

```powershell
.\gradlew.bat updateJUnitTestInventory verifyJUnitTestInventory verifyJUnitTestSignatures --no-daemon --stacktrace
```

不要手改 lock。更新任务会稳定排序。

## Coverage Gates

根项目：

```powershell
.\gradlew.bat test jacocoTestReport jacocoTestCoverageVerification --no-daemon --stacktrace
```

buildSrc：

```powershell
.\gradlew.bat buildSrc:check buildSrc:jacocoTestReport --no-daemon --stacktrace
```

策略：

- 全局门禁防止大面积回退。
- 包级和类级 branch gate 防止关键类被总覆盖率掩盖。
- 补测试优先真实边界输入，不堆 mock。

## Documentation Drift

运行：

```powershell
.\gradlew.bat test --tests "config.DocumentationDriftTest" --no-daemon --stacktrace
```

覆盖：

- README 的 `toml` code fence 可解析。
- `configs/**/*.toml` 可解析。
- profile dry-run 可 resolve。
- `docs/release-readiness.md` 提到真实 Gradle 任务。
- detekt baseline 不存在。
- legacy wrapper 无残留引用。

新增 README TOML 示例时必须是 standalone config。

## Metric Schema Snapshots

Realtime metric 测试锁定：

- 每个 `RealtimeMetricKey` 恰好一个定义；
- CSV header 顺序；
- trial/summary map 顺序；
- 失败 trial 空指标；
- `docs/realtime-metrics.md` 渲染结果。

新增 metric 后必须更新 snapshot 和文档。

## buildSrc TestKit

TestKit 用于验证真实 Gradle task action。要求：

- 使用临时 fixture。
- 使用 fake Git/Maven/Docker，不依赖真实网络或本机工具状态。
- 覆盖 success、failure diagnostics、inputs/outputs、up-to-date、configuration cache。
- 子 Gradle 构建的 JaCoCo exec 会合并进 buildSrc 报告。

典型目标：

- CloudSim Plus source prepare/build/sanitize/verify。
- JUnit inventory/signature tasks。
- warning audit。
- container smoke。
- license policy。

## Adding Tests

新增测试时：

1. 使用 block-bodied `@Test`。
2. 优先测试真实边界，不只测 happy path。
3. 避免全局状态污染。
4. 临时文件使用 `@TempDir`。
5. 固定 random seed。
6. 对 CloudSim 小规模集成测试控制任务数和 VM 数。
7. 更新 JUnit inventory。

## Test Size And Suppress

长测试优先拆：

- fixture builder；
- assertion helper；
- scenario helper；
- fake dependency。

生产兼容 facade 的 `@Suppress("TooManyFunctions")` 可以保留，但必须说明稳定 API 或兼容边界。测试中的 suppress 优先通过 helper 清理。
