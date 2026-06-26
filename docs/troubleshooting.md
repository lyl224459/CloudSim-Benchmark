# Troubleshooting

本文档集中记录本地开发、构建、配置、容器和 CI 常见问题。

## JDK Or Gradle

### Symptom

```text
Unsupported class file major version
Inconsistent JVM-target compatibility
Kotlin does not yet support target
```

### Action

- 确认运行 JDK 是 25+：

```powershell
java -version
.\gradlew.bat --version
```

- 使用仓库 Gradle wrapper，不要使用系统 Gradle。
- 如果 IDE 使用不同 JDK，调整 IDE Gradle JVM。

## CloudSim Plus Submodule Missing

### Symptom

```text
third_party/cloudsimplus missing
submodule checkout is missing
```

### Action

```powershell
git submodule update --init --recursive
.\gradlew.bat verifyCloudSimPlusLock --configuration-cache
```

默认 locked mode 不会自动 fetch/checkout；它只验证已有 submodule。

## CloudSim Plus Lock Drift

### Symptom

```text
lock drift
checkout commit does not match gradle/cloudsimplus.lock
POM version drift
```

### Action

普通开发：

```powershell
git submodule update --init --recursive
.\gradlew.bat verifyCloudSimPlusLock
```

维护者更新 CloudSim Plus：

```powershell
.\gradlew.bat fullCheck -Pcloudsimplus.ref=v8.5.7
.\gradlew.bat updateCloudSimPlusLock -Pcloudsimplus.ref=v8.5.7
```

同时提交 `gradle/cloudsimplus.lock` 和 submodule gitlink。

## Maven Not Installed (CloudSim Plus Source Build Fails)

### Symptom

```text
Execution failed for task ':buildCloudSimPlusFromSource'
> A problem occurred starting process 'command 'mvn.cmd''
```

或者：

```text
Could not find org.cloudsimplus:cloudsimplus:X.Y.Z
Required by: root project 'CloudSim-Benchmark'
```

### Cause

CloudSim Plus 是 Maven 项目，通过 Git 子模块（`third_party/cloudsimplus`）提供源码。`buildCloudSimPlusFromSource` 任务需要调用 `mvn`（Maven）从源码编译出 JAR，才能供主项目依赖解析。

子模块中通常不含 Maven Wrapper（`mvnw.cmd` / `maven-wrapper.jar`），因此系统必须单独安装 Maven。

### Action — 安装 Maven

**Windows（无包管理器）**：

```powershell
# 查看最新版本
Invoke-WebRequest -Uri "https://dlcdn.apache.org/maven/maven-3/" -UseBasicParsing |
  Select-Object -ExpandProperty Links |
  Where-Object { $_.href -match '^\d+\.\d+\.\d+/$' } |
  Select-Object -Last 1 -ExpandProperty href

# 下载（国内建议用阿里云镜像加速）
$ver = "3.9.16"  # 替换为实际最新版
$url = "https://mirrors.aliyun.com/apache/maven/maven-3/$ver/binaries/apache-maven-$ver-bin.zip"
Invoke-WebRequest -Uri $url -OutFile "$env:TEMP\maven.zip" -UseBasicParsing

# 解压到用户目录（避免 Program Files 权限问题）
$installDir = "$env:LOCALAPPDATA\Programs\Maven"
Expand-Archive -Path "$env:TEMP\maven.zip" -DestinationPath $installDir -Force

# 永久加入 PATH
$mavenHome = Get-ChildItem $installDir -Directory | Select-Object -First 1
[Environment]::SetEnvironmentVariable("MAVEN_HOME", "$installDir\$($mavenHome.Name)", "User")
$path = [Environment]::GetEnvironmentVariable("Path", "User")
if ($path -notlike "*$installDir\$($mavenHome.Name)\bin*") {
  [Environment]::SetEnvironmentVariable("Path", "$installDir\$($mavenHome.Name)\bin;$path", "User")
}
```

**macOS / Linux**：

```bash
# Homebrew (macOS)
brew install maven

# SDKMAN (通用)
sdk install maven

# 手动安装
curl -fsSL https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz |
  tar -xz -C ~/bin/
echo 'export PATH="$HOME/bin/apache-maven-3.9.16/bin:$PATH"' >> ~/.bashrc
```

安装后**重新打开终端**验证：

```powershell
mvn --version
```

> **注意**：`winget` 源中不存在 Apache Maven 包，无法通过 `winget install` 安装。

## 首次构建与环境准备

新克隆或新系统上的首次构建流程，按顺序执行：

### 步骤 1：初始化子模块

```powershell
git submodule update --init --recursive
```

### 步骤 2：配置中国镜像源（国内网络推荐）

编辑三个 Gradle 文件，在官方源前添加阿里云镜像，确保**镜像优先、官方回退**：

**`build.gradle.kts`** — 依赖仓库：

```kotlin
repositories {
    maven {
        name = "cloudSimPlusSourceBuild"
        url = uri(layout.buildDirectory.dir("cloudsimplus-m2"))
        mavenContent { includeGroup("org.cloudsimplus") }
    }
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
}
```

**`buildSrc/build.gradle.kts`** — buildSrc 仓库：

```kotlin
repositories {
    maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    gradlePluginPortal()
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
}
```

**`settings.gradle.kts`** — 插件仓库：

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        gradlePluginPortal()
    }
}
```

> 其他常用镜像：腾讯云 `https://mirrors.cloud.tencent.com/nexus/repository/maven-public/`、华为云 `https://repo.huaweicloud.com/repository/maven/`。

### 步骤 3：确保 Maven 可用

参考上节 [Maven 安装](#maven-not-installed-cloudsim-plus-source-build-fails)。

### 步骤 4：同步 JUnit 测试清单

不同 JDK 版本或编译环境下，JUnit 发现的测试入口点可能略有差异：

```powershell
.\gradlew.bat updateJUnitTestInventory --no-configuration-cache
```

### 步骤 5：完整构建

```powershell
.\gradlew.bat build --no-configuration-cache
```

> `--no-configuration-cache` 在首次构建时必须使用，避免配置缓存中残留的依赖解析失败状态阻塞 CloudSim Plus 源码构建任务链。后续增量构建可省略此参数。

## Proxy Or Network

PowerShell dotted property 需要加引号：

```powershell
.\gradlew.bat verifyCloudSimPlusLock '-Dorg.gradle.project.cloudsimplus.gitProxy=http://host:port'
```

Windows `run.cmd` 会读取系统代理并传给 Gradle/Java。

离线模式：

```powershell
.\gradlew.bat verifyCloudSimPlusLock -Pcloudsimplus.offline=true
```

## Warning Audit Fails

运行：

```powershell
pwsh -File scripts/run-build-warning-audit.ps1
```

查看：

```text
build/reports/build-warnings/audit.md
build/reports/build-warnings/*.log
```

允许项是精确白名单。新增 deprecation、native access、Unsafe、JVM target fallback 或 Maven/Jansi/Guava warning 都应先定位来源，不要全局隐藏。

## Detekt Baseline Reappears

项目要求 detekt baseline 不存在。

检查：

```powershell
.\gradlew.bat verifyNoDetektBaseline detekt --no-daemon --stacktrace
```

不要重新生成 baseline。应修复 detekt issue，或对确实需要保留的兼容 facade 添加局部 suppress 和理由。

## JUnit Inventory Fails

如果新增、删除或重命名测试：

```powershell
.\gradlew.bat updateJUnitTestInventory verifyJUnitTestInventory verifyJUnitTestSignatures --no-daemon --stacktrace
```

如果不是有意变化，检查是否有测试方法因返回非 `Unit` 而未被 JUnit 发现。

## README TOML Drift Fails

README 中的 `toml` code fence 会被当作独立配置解析。修复方式：

- 让示例成为完整 standalone config；
- 或改用 `text` code fence；
- 或把长配置放到 `docs/configuration.md` 并引用 `configs/` 文件。

## Podman Or Container Smoke

本地没有 Podman：

```text
containerImageSmoke prints diagnostic and skips
```

CI 没有 Podman：

```text
containerImageSmoke fails
```

生成上下文：

```powershell
.\gradlew.bat prepareContainerImageContext verifyContainerBuildContext
```

常见失败：

- 上下文超过 50 MiB；
- provenance checksum 不一致；
- `.git`、Gradle、源码进入上下文；
- Podman 无法写入 `/app/runs` 挂载目录。
- 镜像不是 UID 10001；
- `/app/runs` 不可写。

## License Policy Fails

运行：

```powershell
.\gradlew.bat generateSupplyChainReports checkLicense --no-daemon --stacktrace --no-configuration-cache
```

处理：

- 确认新增 runtime 依赖许可证。
- 如许可证可接受，更新 `gradle/allowed-licenses.json`。
- 如许可证不可接受，替换依赖或排除 runtime 引入。

## Dependency Verification Fails

依赖升级后需要更新 verification metadata：

```powershell
.\gradlew.bat testClasses buildSrc:testClasses compileJmhKotlin generateSupplyChainReports `
  --write-verification-metadata sha256 `
  --no-daemon --stacktrace --no-configuration-cache
```

只提交相关 checksum diff。

### Gradle 升级后 buildSrc 依赖验证失败

Gradle 大版本或小版本升级（如 9.5.1 → 9.6.0）后，内置 Kotlin 版本变化会引入新的 buildSrc 依赖（如 `kotlin-reflect`、`kotlin-assignment` 的 `.pom` 和 `.module` 文件）。由于 `--write-verification-metadata` 无法捕获 buildSrc 层依赖，需手动计算缺失文件的 SHA256 并补充到 `gradle/verification-metadata.xml`。

**临时绕过**（仅用于诊断，不要长期使用）：

```powershell
.\gradlew.bat build --dependency-verification=off
```

**永久修复**：定位 Gradle 缓存中缺失依赖的实际文件，计算 SHA256 后写入对应 `<component>` 条目。常见缺失项示例：

- `org.gradle.kotlin:gradle-kotlin-dsl-plugins:6.6.4` → `.module`
- `org.jetbrains.kotlin:kotlin-assignment:2.3.21` → `.module`
- `org.jetbrains.kotlin:kotlin-sam-with-receiver:2.3.21` → `.module`
- `org.jetbrains.kotlin:kotlin-reflect:2.3.21` → `.pom`
- `org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:6.6.4` → `.pom`
- `org.jetbrains.kotlin:kotlin-assignment-compiler-plugin-embeddable:2.3.21` → `.pom`
- `org.jetbrains.kotlin:kotlin-sam-with-receiver-compiler-plugin-embeddable:2.3.21` → `.pom`

添加后运行不带 `--dependency-verification=off` 的 `build` 验证。

## Gradle 升级后 IDE 全红 (Unresolved reference)

### Symptom

升级 Gradle wrapper（如 9.5.1 → 9.6.0）后，IDE 中 `build.gradle.kts` 和 `buildSrc/build.gradle.kts` 所有 `import buildlogic.*` 均报 `Unresolved reference`，文件全红。

### Cause

IDE 的 Gradle 同步缓存了旧版本 buildSrc 编译产物。Gradle wrapper 版本变化后，IDE 仍使用旧索引，无法解析 buildSrc 中定义的自定义 Task 类。

### Action

**1. 命令行编译 buildSrc（确认代码无问题）：**

```powershell
.\gradlew.bat buildSrc:classes --no-configuration-cache
```

**2. IDE 重新导入 Gradle 项目：**

- IntelliJ IDEA：右侧 Gradle 面板 → 点击刷新按钮（Reload All Gradle Projects）
- 或 `File` → `Invalidate Caches...` → `Invalidate and Restart`

**3. 确认 IDE Gradle 设置：**

- `File` → `Settings` → `Build Tools` → `Gradle`
- `Distribution`：选择 `Gradle wrapper`
- `Gradle JVM`：选择 JDK 25

重启后 IDE 会下载新 Gradle 发行版、重新编译 buildSrc 并索引所有类。

## Git Global Ignore Permission Noise

### Symptom

```text
warning: unable to access 'C:\Users\admin/.config/git/ignore': Permission denied
```

### Action

这是本机 Git 配置问题，不是项目问题。检查：

```powershell
git config --global core.excludesfile
```

修复文件权限，或把 `core.excludesfile` 改到可读路径。

## Experiment Produces No CSV

检查：

- `csv.enabled` 是否为 false。
- 是否使用了 `--dry-run`。
- `ExperimentOutputContext.experimentDir` 是否为 null。
- 输出目录是否可写。

## Realtime Metrics Look Empty

常见原因：

- 所有 trial 失败，metric 单元格为空。
- 对应 feature 未启用，例如 tenant/topology/autoscaling。
- Google trace 缺少 user/resource/topology metadata。
- 任务被拒绝但不是失败，需要看 rejection 指标。

## Performance Results Are Noisy

Hosted runner 不适合硬门禁。建议：

- 多跑几次；
- 使用固定硬件；
- 关闭后台负载；
- 比较 JMH JSON delta 而不是单次 wall-clock；
- 不跨 JDK/CloudSim Plus lock/GC 参数比较。
