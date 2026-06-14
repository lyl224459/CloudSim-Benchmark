import buildlogic.BuildCloudSimPlusFromSourceTask
import buildlogic.BuildWarningAuditTask
import buildlogic.CliEndToEndSmokeTask
import buildlogic.CloudSimPlusBuildService
import buildlogic.CloudSimPlusLockSupport
import buildlogic.ContainerImageSmokeTask
import buildlogic.PrepareCloudSimPlusSourceTask
import buildlogic.ReleaseManifest
import buildlogic.ReleaseManifestSupport
import buildlogic.SanitizeCloudSimPlusJarManifestTask
import buildlogic.UpdateCloudSimPlusLockTask
import buildlogic.UpdateJUnitTestInventoryTask
import buildlogic.VerifyCloudSimPlusLockTask
import buildlogic.VerifyCloudSimPlusSourceBuildTask
import buildlogic.VerifyJUnitTestInventoryTask
import buildlogic.VerifyJUnitTestSignaturesTask
import buildlogic.VerifyNoDetektBaselineTask
import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    application
    jacoco
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("me.champeau.jmh") version "0.7.3"
}

group = "com.lyl224459"
version = "1.0.0"

description = "CloudSim-Benchmark: 云任务调度算法对比实验平台"

// 动态检测CPU核心数并优化构建提示
val cpuCores = Runtime.getRuntime().availableProcessors()
val workerThreads = cpuCores
val maxReasonableThreads = cpuCores * 2
val finalWorkerThreads = minOf(workerThreads, maxReasonableThreads)

logger.lifecycle("🔧 构建优化 - CPU核心数: $cpuCores, 建议工作线程数: $finalWorkerThreads")
logger.lifecycle("⚡ 并行构建: 已启用 | 构建缓存: 已启用 | 增量编译: 已启用")

val cloudSimPlusGroup = "org.cloudsimplus"
val cloudSimPlusArtifact = "cloudsimplus"
val cloudSimPlusRepositoryUrl = "https://github.com/cloudsimplus/cloudsimplus.git"
val cloudSimPlusSubmoduleDir = layout.projectDirectory.dir("third_party/cloudsimplus")
val cloudSimPlusRawMavenRepo = layout.buildDirectory.dir("cloudsimplus-raw-m2")
val cloudSimPlusLocalMavenRepo = layout.buildDirectory.dir("cloudsimplus-m2")
val cloudSimPlusVersionFile = layout.buildDirectory.file("cloudsimplus-version.txt")
val cloudSimPlusLockFile = layout.projectDirectory.file("gradle/cloudsimplus.lock")
val junitTestInventoryFile = layout.projectDirectory.file("gradle/junit-test-inventory.lock")
val cloudSimPlusLockedMetadata =
    providers.fileContents(cloudSimPlusLockFile).asText.map(CloudSimPlusLockSupport::parse)
val cloudSimPlusActualMetadata =
    providers.fileContents(cloudSimPlusVersionFile).asText.map(CloudSimPlusLockSupport::parse)
val cloudSimPlusMavenCacheDir =
    providers
        .gradleProperty("cloudsimplus.mavenCacheDir")
        .orElse(providers.systemProperty("user.home").map { home -> "$home/.m2/repository" })
val cloudSimPlusMavenCacheDirectory = layout.dir(cloudSimPlusMavenCacheDir.map(::File))
val cloudSimPlusAutoUpdate =
    providers
        .gradleProperty("cloudsimplus.autoUpdate")
        .map(String::toBoolean)
        .orElse(false)
val cloudSimPlusOffline =
    providers
        .gradleProperty("cloudsimplus.offline")
        .map(String::toBoolean)
        .orElse(false)
val cloudSimPlusRequestedRef =
    providers
        .gradleProperty("cloudsimplus.ref")
        .orElse("")
val cloudSimPlusEnforceLock =
    cloudSimPlusAutoUpdate.zip(cloudSimPlusRequestedRef) { autoUpdate, requestedRef ->
        !autoUpdate && requestedRef.isBlank()
    }
val cloudSimPlusNetworkProxy =
    providers
        .gradleProperty("cloudsimplus.gitProxy")
        .orElse("")
val cloudSimPlusGitTimeoutSeconds =
    providers
        .gradleProperty("cloudsimplus.gitTimeoutSeconds")
        .map(String::toLong)
        .orElse(60L)

val buildWarningLogDirectory = layout.buildDirectory.dir("reports/build-warnings/logs")
val buildWarningAuditReport = layout.buildDirectory.file("reports/build-warnings/audit.md")
val cloudSimPlusDependencyVersion =
    cloudSimPlusEnforceLock.zip(cloudSimPlusLockedMetadata) { enforceLock, locked ->
        if (enforceLock) locked.version else providers.gradleProperty("cloudsimplus.version").orNull ?: "latest.release"
    }

repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "cloudSimPlusSourceBuild"
                url = uri(cloudSimPlusLocalMavenRepo.get().asFile)
                metadataSources {
                    mavenPom()
                    artifact()
                }
            }
        }
        filter {
            includeGroup(cloudSimPlusGroup)
        }
    }
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

val cloudSimLogbackConfig = "cloudsim-benchmark-logback.xml"
val cloudSimLogbackJvmArg = "-Dlogback.configurationFile=$cloudSimLogbackConfig"
val cloudSimLogbackFileJvmArg =
    "-Dlogback.configurationFile=${layout.projectDirectory.file("src/main/resources/$cloudSimLogbackConfig").asFile.absolutePath}"

val cloudSimJvmArgs =
    listOf(
        "--add-opens",
        "java.base/java.lang=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.util=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.nio=ALL-UNNAMED",
        "--add-opens",
        "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens",
        "java.base/sun.nio.ch=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
        "-Dfile.encoding=UTF-8",
        "-Dconsole.encoding=UTF-8",
        cloudSimLogbackJvmArg,
        "-XX:+UseZGC",
    )

val performanceJvmArgs =
    listOf(
        "--add-opens",
        "java.base/java.lang=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.util=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.nio=ALL-UNNAMED",
        "--add-opens",
        "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens",
        "java.base/sun.nio.ch=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
        "-Xms1g",
        "-Xmx1g",
        "-XX:+UseG1GC",
        "-Dfile.encoding=UTF-8",
        "-Dconsole.encoding=UTF-8",
        cloudSimLogbackFileJvmArg,
    )

val warningsAsErrorsProvider =
    providers
        .gradleProperty("warningsAsErrors")
        .map { it.toBoolean() }
        .orElse(false)

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget
                .fromTarget("25"),
        )

        // Kotlin 编译优化
        allWarningsAsErrors.set(warningsAsErrorsProvider)
        suppressWarnings.set(false)

        // 添加编译参数优化
        freeCompilerArgs.addAll(
            listOf(
                "-Xlambdas=indy",
            ),
        )
    }
}

val prepareCloudSimPlusSource by tasks.registering(PrepareCloudSimPlusSourceTask::class) {
    group = "build setup"
    description = "初始化 CloudSim Plus submodule，fetch tags，并 checkout 最新 release tag 或 -Pcloudsimplus.ref"
    repositoryUrl.set(cloudSimPlusRepositoryUrl)
    autoUpdate.set(cloudSimPlusAutoUpdate)
    offline.set(cloudSimPlusOffline)
    requestedRef.set(cloudSimPlusRequestedRef)
    lockFile.set(cloudSimPlusLockFile)
    enforceLock.set(cloudSimPlusEnforceLock)
    networkProxy.set(cloudSimPlusNetworkProxy)
    gitTimeoutSeconds.set(cloudSimPlusGitTimeoutSeconds)
    rootDir.set(layout.projectDirectory)
    sourceDir.set(cloudSimPlusSubmoduleDir)
    versionFile.set(cloudSimPlusVersionFile)
}

val verifyCloudSimPlusLock by tasks.registering(VerifyCloudSimPlusLockTask::class) {
    group = "verification"
    description = "验证 CloudSim Plus checkout、实际元数据与 gradle/cloudsimplus.lock 一致"
    dependsOn(prepareCloudSimPlusSource)
    lockFile.set(cloudSimPlusLockFile)
    metadataFile.set(cloudSimPlusVersionFile)
    rootDir.set(layout.projectDirectory)
    sourceDir.set(cloudSimPlusSubmoduleDir)
    enforceLock.set(cloudSimPlusEnforceLock)
}

val updateCloudSimPlusLock by tasks.registering(UpdateCloudSimPlusLockTask::class) {
    group = "build setup"
    description = "将显式 ref 或 latest compatibility build 的实际元数据写入 CloudSim Plus lock"
    dependsOn(prepareCloudSimPlusSource)
    updateAllowed.set(cloudSimPlusEnforceLock.map { !it })
    metadataFile.set(cloudSimPlusVersionFile)
    lockFile.set(cloudSimPlusLockFile)
}

tasks.register<BuildWarningAuditTask>("verifyBuildWarnings") {
    group = "verification"
    description = "验证隔离构建日志只包含已确认的外部工具警告"
    logDirectory.set(buildWarningLogDirectory)
    reportFile.set(buildWarningAuditReport)
}

val cloudSimPlusBuildLock =
    gradle.sharedServices.registerIfAbsent("cloudSimPlusBuildLock", CloudSimPlusBuildService::class) {
        maxParallelUsages.set(1)
    }

val buildCloudSimPlusFromSource by tasks.registering(BuildCloudSimPlusFromSourceTask::class) {
    group = "build"
    description = "使用独立 Maven 依赖缓存构建 CloudSim Plus，并将 JAR/POM stage 到 build/cloudsimplus-raw-m2"

    val sourceDir = cloudSimPlusSubmoduleDir.asFile
    dependsOn(verifyCloudSimPlusLock)
    usesService(cloudSimPlusBuildLock)
    sourceFiles.from(
        fileTree(sourceDir) {
            exclude(".git/**", "target/**")
        },
    )
    this.sourceDir.set(cloudSimPlusSubmoduleDir)
    mavenCacheDir.set(cloudSimPlusMavenCacheDirectory)
    rawMavenRepo.set(cloudSimPlusRawMavenRepo)
    metadataFile.set(cloudSimPlusVersionFile)
    artifactGroup.set(cloudSimPlusGroup)
    artifactName.set(cloudSimPlusArtifact)
    networkProxy.set(cloudSimPlusNetworkProxy)
}

val sanitizeCloudSimPlusJarManifest by tasks.registering(SanitizeCloudSimPlusJarManifestTask::class) {
    group = "build"
    description = "移除源码构建 CloudSim Plus jar 中不兼容 JDK 25/Windows 的 manifest Class-Path"

    dependsOn(buildCloudSimPlusFromSource)
    usesService(cloudSimPlusBuildLock)
    rawMavenRepo.set(cloudSimPlusRawMavenRepo)
    sanitizedMavenRepo.set(cloudSimPlusLocalMavenRepo)
    artifactGroup.set(cloudSimPlusGroup)
    artifactName.set(cloudSimPlusArtifact)
    artifactVersion.set(cloudSimPlusActualMetadata.map { metadata -> metadata.version })
}

val verifyCloudSimPlusSourceBuild by tasks.registering(VerifyCloudSimPlusSourceBuildTask::class) {
    group = "verification"
    description = "验证 compile/runtime classpath 中的 CloudSim Plus 来自源码构建本地 Maven 仓库"

    dependsOn(sanitizeCloudSimPlusJarManifest)
    localMavenRepo.set(cloudSimPlusLocalMavenRepo)
    artifactName.set(cloudSimPlusArtifact)
    compileClasspath.from(configurations.named("compileClasspath"))
    runtimeClasspath.from(configurations.named("runtimeClasspath"))
    testRuntimeClasspath.from(configurations.named("testRuntimeClasspath"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(sanitizeCloudSimPlusJarManifest)
}

tasks.withType<Test>().configureEach {
    dependsOn(sanitizeCloudSimPlusJarManifest)
}

tasks.withType<JavaExec>().configureEach {
    dependsOn(sanitizeCloudSimPlusJarManifest)
}

tasks.withType<Jar>().configureEach {
    dependsOn(sanitizeCloudSimPlusJarManifest)
}

dependencies {
    implementation("$cloudSimPlusGroup:$cloudSimPlusArtifact:${cloudSimPlusDependencyVersion.get()}")
    implementation("org.apache.commons:commons-math3:3.6.1")

    // 日志库：kotlin-logging (Kotlin友好的日志API)
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // 日志实现：slf4j + logback
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // TOML配置文件解析库
    implementation("com.akuleshov7:ktoml-core:0.7.1")
    implementation("com.akuleshov7:ktoml-file:0.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Kotlin协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    testImplementation(kotlin("test"))

    // JUnit 5 测试框架
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")

    // Mockito for mocking
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")

    // AssertJ for fluent assertions
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    // 测试JVM参数优化
    jvmArgs(listOf("-Xmx2g", "-XX:MaxGCPauseMillis=50") + cloudSimJvmArgs)

    // 测试报告配置
    reports {
        html.required.set(true)
        junitXml.required.set(true)
    }

    // JUnit 5 配置
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")

    // 测试超时设置
    systemProperty("junit.jupiter.execution.timeout.default", "60s")
}

jacoco {
    reportsDirectory.set(layout.buildDirectory.dir("reports/jacoco"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named<Test>("test"))

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named<Test>("test"))

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.68".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()
            }
        }
        rule {
            element = "PACKAGE"
            includes = listOf("datacenter")
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.45".toBigDecimal()
            }
        }
    }
}

val verifyJUnitTestSignatures by tasks.registering(VerifyJUnitTestSignaturesTask::class) {
    group = "verification"
    description = "验证所有 JUnit @Test 方法返回 void/Unit，防止测试被静默忽略"

    dependsOn(tasks.named("testClasses"))
    testClassesDirs.from(sourceSets["test"].output.classesDirs)
    testRuntimeClasspath.from(sourceSets["test"].runtimeClasspath)
}

val updateJUnitTestInventory by tasks.registering(UpdateJUnitTestInventoryTask::class) {
    group = "verification"
    description = "显式更新已编译 JUnit 测试入口精确清单"

    dependsOn(tasks.named("testClasses"))
    testClassesDirs.from(sourceSets["test"].output.classesDirs)
    testRuntimeClasspath.from(sourceSets["test"].runtimeClasspath)
    inventoryFile.set(junitTestInventoryFile)
}

val verifyJUnitTestInventory by tasks.registering(VerifyJUnitTestInventoryTask::class) {
    group = "verification"
    description = "验证已编译 JUnit 测试入口与提交的精确清单一致"

    dependsOn(tasks.named("testClasses"))
    testClassesDirs.from(sourceSets["test"].output.classesDirs)
    testRuntimeClasspath.from(sourceSets["test"].runtimeClasspath)
    inventoryFile.set(junitTestInventoryFile)
    mustRunAfter(updateJUnitTestInventory)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "22"

    var originalJavaVersion: String? = null
    doFirst {
        originalJavaVersion = System.getProperty("java.version")
        val majorVersion =
            originalJavaVersion
                ?.substringBefore(".")
                ?.toIntOrNull()
        if (majorVersion != null && majorVersion > 22) {
            // Remove this workaround after detekt and baseline generation both pass on the CI JDK without it.
            System.setProperty("java.version", "22")
        }
    }
    doLast {
        originalJavaVersion?.let { System.setProperty("java.version", it) }
    }

    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

val verifyNoDetektBaseline by tasks.registering(VerifyNoDetektBaselineTask::class) {
    group = "verification"
    description = "验证项目未重新引入 detekt baseline 文件或配置"

    baselineFiles.from(layout.projectDirectory.file("detekt-baseline.xml"))
    buildScripts.from(
        layout.projectDirectory.file("build.gradle.kts"),
        layout.projectDirectory.file("buildSrc/build.gradle.kts"),
    )
}

tasks.named("check") {
    dependsOn(
        "ktlintCheck",
        "detekt",
        "jacocoTestReport",
        "jacocoTestCoverageVerification",
        verifyJUnitTestSignatures,
        verifyJUnitTestInventory,
        verifyNoDetektBaseline,
        verifyCloudSimPlusSourceBuild,
    )
}

jmh {
    includes.set(listOf(".*CloudSimPerformanceBenchmarks.*"))
    warmupIterations.set(1)
    iterations.set(1)
    fork.set(1)
    timeOnIteration.set("250ms")
    resultFormat.set("JSON")
    resultsFile.set(layout.buildDirectory.file("reports/performance/jmh-results.json"))
    profilers.set(listOf("gc"))
    jvmArgs.set(performanceJvmArgs)
}

val exampleConfigFiles =
    layout.projectDirectory.dir("configs/examples").asFileTree.matching {
        include("*.toml")
    }

val validateExampleConfigTasks =
    exampleConfigFiles.files.sortedBy { it.name }.map { configFile ->
        val taskName =
            "validateExampleConfig" +
                configFile.nameWithoutExtension
                    .split('_', '-', '.')
                    .joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }
        tasks.register<JavaExec>(taskName) {
            group = "verification"
            description = "验证示例配置文件 ${configFile.name}"

            mainClass.set("MainKt")
            classpath = sourceSets["main"].runtimeClasspath
            dependsOn("classes", "processResources")
            args("config", "validate", "--config", configFile.path)
            jvmArgs = cloudSimJvmArgs
            systemProperty("file.encoding", "UTF-8")
            inputs.file(configFile)
        }
    }
check(validateExampleConfigTasks.isNotEmpty()) { "No example config files found in configs/examples" }

val validateExampleConfigs by tasks.registering {
    group = "verification"
    description = "验证示例配置文件可被解析并通过配置校验"

    dependsOn(validateExampleConfigTasks)
    inputs.files(exampleConfigFiles)
}

val quickCheck by tasks.registering {
    group = "verification"
    description = "执行本地快检：主代码编译、测试编译和示例配置校验"

    dependsOn("classes", "compileTestKotlin", validateExampleConfigs)
}

fun ProviderFactory.benchmarkProperty(
    name: String,
    defaultValue: String,
): String = gradleProperty(name).orElse(defaultValue).get()

val benchmarkPerformance by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "运行实时可靠性性能基准，默认覆盖 100/1000/10000 cloudlets 并输出 JSON"

    mainClass.set("datacenter.RealtimePerformanceBenchmarkRunnerKt")
    classpath = sourceSets["main"].runtimeClasspath
    dependsOn("classes", "processResources")
    jvmArgs = cloudSimJvmArgs
    systemProperty("file.encoding", "UTF-8")
    args(
        "--sizes",
        providers.benchmarkProperty("benchmarkSizes", "100,1000,10000"),
        "--algorithms",
        providers.benchmarkProperty("benchmarkAlgorithms", "PSO,WOA,GWO,HHO,REALTIME_MIN_LOAD"),
        "--runs",
        providers.benchmarkProperty("benchmarkRuns", "3"),
        "--seed",
        providers.benchmarkProperty("benchmarkSeed", "0"),
        "--population",
        providers.benchmarkProperty("benchmarkPopulation", "10"),
        "--maxIter",
        providers.benchmarkProperty("benchmarkMaxIter", "10"),
        "--output",
        providers.benchmarkProperty("benchmarkOutput", "build/reports/realtime-performance/benchmark-results.json"),
    )
}

val benchmarkPerformanceSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "运行极小规模性能基准 smoke test，并验证 JSON 输出链路"

    mainClass.set("datacenter.RealtimePerformanceBenchmarkRunnerKt")
    classpath = sourceSets["main"].runtimeClasspath
    dependsOn("classes", "processResources")
    jvmArgs = cloudSimJvmArgs
    systemProperty("file.encoding", "UTF-8")
    args(
        "--sizes",
        providers.benchmarkProperty("benchmarkSmokeSizes", "10"),
        "--algorithms",
        providers.benchmarkProperty("benchmarkSmokeAlgorithms", "REALTIME_MIN_LOAD"),
        "--runs",
        providers.benchmarkProperty("benchmarkSmokeRuns", "1"),
        "--seed",
        providers.benchmarkProperty("benchmarkSeed", "0"),
        "--population",
        providers.benchmarkProperty("benchmarkSmokePopulation", "3"),
        "--maxIter",
        providers.benchmarkProperty("benchmarkSmokeMaxIter", "3"),
        "--output",
        providers.benchmarkProperty("benchmarkSmokeOutput", "build/reports/realtime-performance/benchmark-smoke-results.json"),
    )
}

val generatePerformanceTrendReport by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "根据 JMH JSON 生成非阻断性能趋势 Markdown 报告"

    val jmhResults = layout.buildDirectory.file("reports/performance/jmh-results.json")
    val trendReport = layout.buildDirectory.file("reports/performance/performance-trend.md")
    val baseline = providers.gradleProperty("performanceBaseline")

    mainClass.set("datacenter.PerformanceTrendReportKt")
    classpath = sourceSets["main"].runtimeClasspath
    dependsOn("jmh", "classes", "processResources")
    jvmArgs = cloudSimJvmArgs
    systemProperty("file.encoding", "UTF-8")
    inputs.file(jmhResults)
    baseline.orNull?.let { inputs.file(it) }
    outputs.file(trendReport)

    doFirst {
        val argsList =
            mutableListOf(
                "--input",
                jmhResults.get().asFile.absolutePath,
                "--output",
                trendReport.get().asFile.absolutePath,
            )
        baseline.orNull?.let { baselinePath ->
            argsList += listOf("--baseline", baselinePath)
        }
        args = argsList
    }
}

val benchmarkPerformanceTrend by tasks.registering {
    group = "verification"
    description = "运行 JMH 性能趋势报告；只生成报告，不作为失败门禁"

    dependsOn("jmh", generatePerformanceTrendReport)
}

val generateRealtimeMetricDocs by tasks.registering(JavaExec::class) {
    group = "documentation"
    description = "根据 RealtimeMetricSchema 生成实时指标文档"

    mainClass.set("datacenter.RealtimeMetricDocumentationGeneratorKt")
    classpath = sourceSets["main"].runtimeClasspath
    dependsOn("classes", "processResources")
    jvmArgs = cloudSimJvmArgs
    systemProperty("file.encoding", "UTF-8")
    args(providers.gradleProperty("realtimeMetricDocsOutput").orElse("docs/realtime-metrics.md").get())
}

val fullCheck by tasks.registering {
    group = "verification"
    description = "执行完整校验：check、test、fatJar 和示例配置校验"

    dependsOn("check", "test", "fatJar", validateExampleConfigs, "verifyReleaseAssets", "fatJarHelpSmoke", "cliEndToEndSmoke")
}

// 创建测试覆盖率任务
tasks.register("testWithCoverage") {
    dependsOn("test", "jacocoTestReport")
    doLast {
        logger.lifecycle("🧪 测试和覆盖率报告完成 - 查看 reports/tests/test/index.html 与 reports/jacoco/test/html/index.html")
    }
}

application {
    mainClass.set("MainKt")
    applicationDefaultJvmArgs = cloudSimJvmArgs
}

// 复制配置文件到构建目录
tasks.processResources {
    from("cloudsim-benchmark.properties") {
        include("*.properties")
        into("config")
    }
}

tasks.named<JavaExec>("run") {
    classpath = sourceSets["main"].runtimeClasspath
    // 添加模块系统相关参数、编码设置和 ZGC 优化
    jvmArgs = cloudSimJvmArgs
    // 设置标准输出编码
    systemProperty("file.encoding", "UTF-8")
}

val isCiBuildProvider = providers.environmentVariable("CI").map { it == "true" || it == "1" }.orElse(false)
val isGitHubActionsProvider =
    providers.environmentVariable("GITHUB_ACTIONS").map { it.equals("true", ignoreCase = true) }.orElse(false)
val forceJarCompressionProvider = providers.gradleProperty("compress").map { it == "true" }.orElse(false)
val skipJarCompressionProvider = providers.gradleProperty("skipCompress").map { true }.orElse(false)
val fastBuildProvider = providers.gradleProperty("fast").map { true }.orElse(false)

// 创建 fat jar 任务
tasks.register<Jar>("fatJar") {
    val compressedEntries =
        (isCiBuildProvider.get() || forceJarCompressionProvider.get()) &&
            !(skipJarCompressionProvider.get() || fastBuildProvider.get())
    archiveBaseName.set("cloudsim-benchmark")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    entryCompression = if (compressedEntries) ZipEntryCompression.DEFLATED else ZipEntryCompression.STORED

    // 优化：只包含必要的类文件
    from(sourceSets.main.get().output)

    from({
        configurations.runtimeClasspath
            .get()
            .filter { it.name.endsWith("jar") }
            .filter { jar ->
                // 排除不必要的依赖以减少JAR大小和构建时间
                val name = jar.name.lowercase()
                !name.contains("kotlin-test") &&
                    !name.contains("junit") &&
                    !name.contains("mockito") &&
                    !name.contains("assertj") &&
                    !name.contains("byte-buddy") &&
                    !name.contains("objenesis")
            }.map { zipTree(it) }
    })

    manifest {
        attributes["Main-Class"] = "MainKt"
        // 添加优化标志
        attributes["Implementation-Version"] = project.version
    }

    // 排除不必要的文件以减少大小
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "logback.xml", "logback-test.xml")

    // 确保在 CI/发布环境中使用压缩（继承自 tasks.withType<Zip> 的配置）
    doFirst {
        if (compressedEntries) {
            logger.lifecycle("📦 发布模式：fatJar 启用压缩（减小体积）")
        } else {
            logger.lifecycle("⚡ 开发模式：fatJar 禁用压缩（提升构建速度）")
        }
    }
}

val fatJarHelpSmoke by tasks.registering(Exec::class) {
    group = "verification"
    description = "运行 fatJar --help，验证可执行 JAR 基础启动链路"

    val fatJarTask = tasks.named<Jar>("fatJar")
    val fatJarFile = fatJarTask.flatMap { it.archiveFile }
    val smokeJvmArgs = cloudSimJvmArgs.toList()

    dependsOn(fatJarTask)
    inputs.file(fatJarFile)

    doFirst {
        commandLine(listOf("java") + smokeJvmArgs + listOf("-jar", fatJarFile.get().asFile.absolutePath, "--help"))
    }
}

val verifyReleaseAssets by tasks.registering {
    group = "verification"
    description = "验证发布包运行所需脚本、JAR 和日志配置文件存在且引用一致"

    val fatJarTask = tasks.named<Jar>("fatJar")
    val fatJarFile = fatJarTask.flatMap { it.archiveFile }
    val requiredFiles =
        listOf(
            layout.projectDirectory.file("run.cmd").asFile,
            layout.projectDirectory.file("scripts/run").asFile,
            layout.projectDirectory.file("scripts/run.bat").asFile,
            layout.projectDirectory.file("src/main/resources/cloudsim-benchmark-logback.xml").asFile,
        )
    val scriptFiles = requiredFiles.take(3)
    val expectedLogbackConfig = cloudSimLogbackConfig

    dependsOn(fatJarTask)
    inputs.files(requiredFiles)
    inputs.file(fatJarFile)

    doLast {
        requiredFiles.forEach { requiredFile ->
            check(requiredFile.isFile) { "Missing release asset: ${requiredFile.path}" }
        }

        val fatJar = fatJarFile.get().asFile
        check(fatJar.isFile) { "Missing fatJar artifact: ${fatJar.path}" }

        scriptFiles.forEach { script ->
            check(script.readText().contains(expectedLogbackConfig)) {
                "Script ${script.path} does not reference $expectedLogbackConfig"
            }
        }
    }
}

val releaseArtifactsDir = layout.buildDirectory.dir("release-artifacts")
val releaseVersion = providers.gradleProperty("releaseVersion").orElse(project.version.toString()).get()
val releasePackageRootName = "cloudsim-benchmark-$releaseVersion"
val releaseJarName = "cloudsim-benchmark-$releaseVersion.jar"
val windowsPackageName = "cloudsim-benchmark-$releaseVersion-windows.zip"
val unixPackageName = "cloudsim-benchmark-$releaseVersion-unix.tar.gz"
val sourcePackageName = "cloudsim-benchmark-$releaseVersion-source.zip"
val releaseManifestName = "release-manifest.txt"
val isWindowsHost = System.getProperty("os.name").lowercase().contains("windows")

val releaseRuntimeFiles =
    listOf(
        layout.projectDirectory.file("configs"),
        layout.projectDirectory.file("data"),
        layout.projectDirectory.file("README.md"),
        layout.projectDirectory.file("LICENSE"),
    )

val copyReleaseJar by tasks.registering(Copy::class) {
    group = "distribution"
    description = "复制 fatJar 到发布产物目录"

    val fatJarTask = tasks.named<Jar>("fatJar")
    val fatJarFile = fatJarTask.flatMap { it.archiveFile }
    val targetJarName = releaseJarName

    dependsOn(fatJarTask)
    from(fatJarFile)
    into(releaseArtifactsDir)
    rename { targetJarName }
}

val packageWindowsRelease by tasks.registering(Zip::class) {
    group = "distribution"
    description = "生成 Windows 发布 zip"

    val fatJarTask = tasks.named<Jar>("fatJar")
    val fatJarFile = fatJarTask.flatMap { it.archiveFile }
    val archiveName = windowsPackageName
    val packageRootName = releasePackageRootName
    val logbackConfigName = cloudSimLogbackConfig
    val runtimeFiles = releaseRuntimeFiles.toList()

    dependsOn(fatJarTask)
    archiveFileName.set(archiveName)
    destinationDirectory.set(releaseArtifactsDir)

    into(packageRootName) {
        from(fatJarFile) {
            rename { "cloudsim-benchmark-all.jar" }
        }
        from(layout.projectDirectory.file("run.cmd"))
        from(layout.projectDirectory.file("scripts/run.bat")) {
            into("scripts")
        }
        from(layout.projectDirectory.file("src/main/resources/cloudsim-benchmark-logback.xml")) {
            rename { logbackConfigName }
        }
        runtimeFiles.forEach { runtimeFile ->
            from(runtimeFile)
        }
    }
}

val packageUnixRelease by tasks.registering(Tar::class) {
    group = "distribution"
    description = "生成 Unix 发布 tar.gz"

    val fatJarTask = tasks.named<Jar>("fatJar")
    val fatJarFile = fatJarTask.flatMap { it.archiveFile }
    val archiveName = unixPackageName
    val packageRootName = releasePackageRootName
    val logbackConfigName = cloudSimLogbackConfig
    val runtimeFiles = releaseRuntimeFiles.toList()

    dependsOn(fatJarTask)
    archiveFileName.set(archiveName)
    destinationDirectory.set(releaseArtifactsDir)
    compression = Compression.GZIP

    into(packageRootName) {
        from(fatJarFile) {
            rename { "cloudsim-benchmark-all.jar" }
        }
        from(layout.projectDirectory.file("scripts/run")) {
            into("scripts")
            filePermissions {
                unix("rwxr-xr-x")
            }
        }
        from(layout.projectDirectory.file("scripts/run.bat")) {
            into("scripts")
        }
        from(layout.projectDirectory.file("run.cmd"))
        from(layout.projectDirectory.file("src/main/resources/cloudsim-benchmark-logback.xml")) {
            rename { logbackConfigName }
        }
        runtimeFiles.forEach { runtimeFile ->
            from(runtimeFile)
        }
    }
}

val packageSourceRelease by tasks.registering(Zip::class) {
    group = "distribution"
    description = "生成源码发布 zip"

    val archiveName = sourcePackageName

    archiveFileName.set(archiveName)
    destinationDirectory.set(releaseArtifactsDir)

    from(layout.projectDirectory) {
        include("src/**")
        include("configs/**")
        include("data/**")
        include("scripts/**")
        include("gradle/**")
        include("*.gradle.kts")
        include("gradle.properties")
        include("gradlew*")
        include("run.cmd")
        include("Containerfile")
        include(".gitmodules")
        include("third_party/**")
        include("README.md")
        include("LICENSE")
        exclude("build/**")
        exclude(".gradle/**")
    }
}

val generateReleaseManifest by tasks.registering {
    group = "distribution"
    description = "生成发布产物清单"

    val manifestFile = releaseArtifactsDir.map { it.file(releaseManifestName) }
    val expectedAssets =
        listOf(
            releaseJarName,
            windowsPackageName,
            unixPackageName,
            sourcePackageName,
            releaseManifestName,
        )
    val metadataFile = cloudSimPlusVersionFile

    dependsOn(copyReleaseJar, packageWindowsRelease, packageUnixRelease, packageSourceRelease, verifyCloudSimPlusLock)
    inputs.file(metadataFile)
    outputs.file(manifestFile)

    doLast {
        val output = manifestFile.get().asFile
        output.parentFile.mkdirs()
        val manifest =
            ReleaseManifest(
                format = ReleaseManifestSupport.CURRENT_FORMAT,
                cloudSimPlus = CloudSimPlusLockSupport.read(metadataFile.get().asFile),
                assets = expectedAssets,
            )
        output.writeText(manifest.render())
    }
}

val packageReleaseAssets by tasks.registering {
    group = "distribution"
    description = "生成所有发布产物和清单"

    dependsOn(copyReleaseJar, packageWindowsRelease, packageUnixRelease, packageSourceRelease, generateReleaseManifest)
}

val verifyReleaseManifest by tasks.registering {
    group = "verification"
    description = "验证发布产物清单与实际产物一致"

    val artifactsDir = releaseArtifactsDir
    val manifestFile = artifactsDir.map { it.file(releaseManifestName) }
    val expectedAssets = listOf(releaseJarName, windowsPackageName, unixPackageName, sourcePackageName, releaseManifestName)
    val expectedMetadata = cloudSimPlusLockedMetadata
    val enforceLock = cloudSimPlusEnforceLock

    dependsOn(packageReleaseAssets)
    inputs.file(manifestFile)
    inputs.property("enforceCloudSimPlusLock", enforceLock)

    doLast {
        val dir = artifactsDir.get().asFile
        val manifest = ReleaseManifestSupport.parse(manifestFile.get().asFile.readText())
        ReleaseManifestSupport.validate(
            manifest = manifest,
            expectedCloudSimPlus = expectedMetadata.get().takeIf { enforceLock.get() },
            expectedAssets = expectedAssets,
        )
        expectedAssets.forEach { asset ->
            check(File(dir, asset).isFile) { "Missing release asset listed in manifest: $asset" }
        }
    }
}

val verifyWindowsReleasePackage by tasks.registering(Exec::class) {
    group = "verification"
    description = "解压 Windows 发布 zip 并运行 run.cmd --help"

    val packageFile = packageWindowsRelease.flatMap { it.archiveFile }
    val smokeDir = layout.buildDirectory.dir("release-smoke/windows")
    val packageRootName = releasePackageRootName
    val runOnWindows = isWindowsHost

    onlyIf { runOnWindows }
    dependsOn(copyReleaseJar, packageWindowsRelease)
    inputs.file(packageFile)

    doFirst {
        val smokeRoot = smokeDir.get().asFile
        smokeRoot.deleteRecursively()
        smokeRoot.mkdirs()
        commandLine(
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-Command",
            """
            Expand-Archive -Path '${packageFile.get().asFile.absolutePath}' -DestinationPath '${smokeRoot.absolutePath}' -Force
            & '${File(smokeRoot, "$packageRootName/run.cmd").absolutePath}' --help
            """.trimIndent(),
        )
    }
}

val verifyUnixReleasePackage by tasks.registering(Exec::class) {
    group = "verification"
    description = "解压 Unix 发布 tar.gz 并运行 scripts/run --help"

    val packageFile = packageUnixRelease.flatMap { it.archiveFile }
    val smokeDir = layout.buildDirectory.dir("release-smoke/unix")
    val packageRootName = releasePackageRootName
    val runOnUnix = !isWindowsHost

    onlyIf { runOnUnix }
    dependsOn(copyReleaseJar, packageUnixRelease)
    inputs.file(packageFile)

    doFirst {
        val smokeRoot = smokeDir.get().asFile
        smokeRoot.deleteRecursively()
        smokeRoot.mkdirs()
        commandLine(
            "bash",
            "-lc",
            "tar -xzf '${packageFile.get().asFile.absolutePath}' -C '${smokeRoot.absolutePath}' && " +
                "bash '${File(smokeRoot, "$packageRootName/scripts/run").absolutePath}' --help",
        )
    }
}

val verifyReleasePackage by tasks.registering {
    group = "verification"
    description = "验证当前平台发布包可解压并运行"

    dependsOn(verifyReleaseManifest, fatJarHelpSmoke)
    if (isWindowsHost) {
        dependsOn(verifyWindowsReleasePackage)
    } else {
        dependsOn(verifyUnixReleasePackage)
    }
}

val containerImageSmoke by tasks.registering(ContainerImageSmokeTask::class) {
    group = "verification"
    description = "构建容器镜像并运行 --help smoke"

    dependsOn("fatJar")
    imageName.set("cloudsim-benchmark:smoke")
    dockerExecutable.set("docker")
    ci.set(isCiBuildProvider)
    useBuildx.set(isGitHubActionsProvider)
    useGitHubActionsCache.set(isGitHubActionsProvider)
    contextDirectory.set(layout.projectDirectory)
    containerFile.set(layout.projectDirectory.file("Containerfile"))
}

val cliEndToEndSmoke by tasks.registering(CliEndToEndSmokeTask::class) {
    group = "verification"
    description = "通过 fatJar 验证 help/list/config validate 和 batch/realtime dry-run 生产入口"

    val fatJarTask = tasks.named<Jar>("fatJar")
    dependsOn(fatJarTask)
    executableJar.set(fatJarTask.flatMap { it.archiveFile })
    exampleConfig.set(layout.projectDirectory.file("configs/examples/single_config_example.toml"))
    javaExecutable.set("java")
    jvmArguments.set(cloudSimJvmArgs)
    dryRunRoot.set(layout.buildDirectory.dir("tmp/cli-end-to-end-smoke/dry-run-output"))
    reportFile.set(layout.buildDirectory.file("reports/cli-end-to-end-smoke/report.txt"))
}

// 优化 Zip 任务性能 - 根据环境自动选择压缩策略
tasks.withType<Zip> {
    val compressedEntries =
        (isCiBuildProvider.get() || forceJarCompressionProvider.get()) &&
            !(skipJarCompressionProvider.get() || fastBuildProvider.get())
    isZip64 = true

    entryCompression = if (compressedEntries) ZipEntryCompression.DEFLATED else ZipEntryCompression.STORED
    isPreserveFileTimestamps = false // 移除时间戳以提升构建缓存命中率
    isReproducibleFileOrder = true
}

// 优化复制任务
tasks.withType<Copy> {
    // 启用文件追踪以支持增量构建
    includeEmptyDirs = false
}

// 内存清理任务
tasks.register("memoryCleanup") {
    group = "build"
    description = "执行内存清理和GC"

    doLast {
        println("🧹 执行内存清理...")
        System.gc()

        val runtime = Runtime.getRuntime()
        val beforeCleanup = runtime.freeMemory()
        Thread.sleep(100) // 给GC一些时间
        val afterCleanup = runtime.freeMemory()

        val freedMemory = afterCleanup - beforeCleanup
        println("✅ 内存清理完成")
        println("  清理前可用内存: ${beforeCleanup / 1024 / 1024}MB")
        println("  清理后可用内存: ${afterCleanup / 1024 / 1024}MB")
        println("  释放内存: ${freedMemory / 1024 / 1024}MB")
    }
}

// 构建健康检查任务
tasks.register("buildHealthCheck") {
    group = "build"
    description = "执行构建健康检查，包括内存和性能监控"

    dependsOn("memoryInfo", "memoryReport")

    doLast {
        println("🏥 构建健康检查完成")
        println("📊 检查项目: CloudSim-Benchmark")
        println("⏱️  检查时间: ${System.currentTimeMillis()}")

        // 检查关键文件是否存在
        val criticalFiles =
            listOf(
                "src/main/kotlin/Main.kt",
                "build.gradle.kts",
                "gradle.properties",
            )

        var allFilesPresent = true
        criticalFiles.forEach { filePath ->
            if (file(filePath).exists()) {
                println("✅ $filePath - 存在")
            } else {
                println("❌ $filePath - 缺失")
                allFilesPresent = false
            }
        }

        if (allFilesPresent) {
            println("🎉 项目健康状态: 良好")
        } else {
            println("⚠️  项目健康状态: 需要修复")
        }
    }
}

// 创建运行脚本，设置正确的编码
tasks.register<CreateStartScripts>("createRunScript") {
    applicationName = "run-comparison"
    mainClass.set("MainKt")
    outputDir =
        layout.buildDirectory
            .dir("scripts")
            .get()
            .asFile
    classpath = tasks.jar
        .get()
        .outputs.files + configurations.runtimeClasspath.get()

    doLast {
        val windowsScript = file("$outputDir/$applicationName.bat")
        if (windowsScript.exists()) {
            val content = windowsScript.readText()
            windowsScript.writeText(
                "@echo off\n" +
                    "chcp 65001 >nul\n" +
                    content.replace(
                        "set DEFAULT_JVM_OPTS=",
                        "set DEFAULT_JVM_OPTS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 $cloudSimLogbackJvmArg ",
                    ),
            )
        }
    }
}

// ========== Gradle 运行任务 ==========

fun Provider<String>.orBlank(): String = orElse("").get()

fun registerCloudSimRunTask(
    taskName: String,
    runMode: String,
    taskDescription: String,
    defaultTasks: String? = null,
) {
    tasks.register<JavaExec>(taskName) {
        group = "application"
        description = taskDescription
        mainClass.set("MainKt")
        classpath = sourceSets["main"].runtimeClasspath
        dependsOn("classes", "processResources")

        val algorithms = providers.gradleProperty("algorithms").orBlank()
        val seed = providers.gradleProperty("seed").orBlank()
        val taskCounts = providers.gradleProperty("cloudletCounts").orElse(defaultTasks ?: "").get()
        val dryRun =
            providers
                .gradleProperty("dryRun")
                .map(String::toBoolean)
                .orElse(false)
                .get()

        val argsList = mutableListOf("run", "--mode", runMode)
        if (taskCounts.isNotBlank()) {
            argsList.addAll(listOf("--tasks", taskCounts))
        }
        if (algorithms.isNotBlank()) {
            argsList.addAll(listOf("--algorithms", algorithms))
        }
        if (seed.isNotBlank()) {
            argsList.addAll(listOf("--seed", seed))
        }
        if (dryRun) {
            argsList.add("--dry-run")
        }

        args = argsList
        jvmArgs = cloudSimJvmArgs
        systemProperty("file.encoding", "UTF-8")
    }
}

/**
 * 批处理模式运行任务
 * 用法:
 *   gradle runBatch                                    # 运行默认 profile / 模式
 *   gradle runBatch -Palgorithms=PSO,WOA              # 覆盖算法列表
 *   gradle runBatch -Palgorithms=PSO,WOA -Pseed=42     # 指定算法和随机种子
 */
registerCloudSimRunTask("runBatch", "batch", "运行批处理调度模式实验")

/**
 * 实时调度模式运行任务
 * 用法:
 *   gradle runRealtime                                    # 运行默认 profile / 模式
 *   gradle runRealtime -Palgorithms=PSO_REALTIME,WOA_REALTIME  # 覆盖算法列表
 *   gradle runRealtime -Palgorithms=PSO_REALTIME,WOA_REALTIME -Pseed=123  # 指定算法和随机种子
 */
registerCloudSimRunTask("runRealtime", "realtime", "运行实时调度模式实验")

/**
 * 批量任务数实验任务
 * 用法:
 *   gradle runBatchMulti                                    # 默认任务数 / profile
 *   gradle runBatchMulti -PcloudletCounts=50,100,200,500,1000  # 指定任务数
 *   gradle runBatchMulti -PcloudletCounts=50,100,200 -Palgorithms=PSO,WOA  # 指定任务数和算法
 *   gradle runBatchMulti -PcloudletCounts=50,100,200 -Palgorithms=PSO,WOA -Pseed=42  # 完整参数
 */
registerCloudSimRunTask(
    "runBatchMulti",
    "batch-multi",
    "运行批量任务数实验",
    defaultTasks = "50,100,200,500",
)

// 实时调度模式批量任务数实验任务
registerCloudSimRunTask(
    "runRealtimeMulti",
    "realtime-multi",
    "运行实时调度模式批量任务数实验",
    defaultTasks = "50,100,200,500",
)

// 内存监控任务
tasks.register("memoryInfo") {
    group = "build"
    description = "显示当前JVM内存使用情况"

    doLast {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        val freeMemory = runtime.freeMemory() / 1024 / 1024
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory() / 1024 / 1024

        println("📊 JVM内存使用情况:")
        println("  总内存: ${totalMemory}MB")
        println("  已用内存: ${usedMemory}MB")
        println("  可用内存: ${freeMemory}MB")
        println("  最大内存: ${maxMemory}MB")
        println("  使用率: ${String.format("%.1f", usedMemory.toDouble() / maxMemory * 100)}%")

        // 显示构建优化状态
        val cpuCores = Runtime.getRuntime().availableProcessors()
        println("🔧 构建配置:")
        println("  CPU核心数: $cpuCores")
        println("  工作线程数: ${System.getProperty("org.gradle.workers.max", "auto")}")
        println("  JVM最大堆: ${System.getProperty("java.vm.name", "unknown")}")
    }
}

tasks.register("memoryReport") {
    group = "build"
    description = "生成详细的内存使用报告"

    doLast {
        val reportFile =
            layout.buildDirectory
                .file("reports/memory-report.txt")
                .get()
                .asFile
        reportFile.parentFile.mkdirs()

        val runtime = Runtime.getRuntime()
        val sb = StringBuilder()

        sb.appendLine("=== CloudSim-Benchmark 内存使用报告 ===")
        sb.appendLine("生成时间: ${System.currentTimeMillis()} (毫秒时间戳)")
        sb.appendLine()
        sb.appendLine("JVM内存信息:")
        sb.appendLine("  总内存: ${runtime.totalMemory() / 1024 / 1024}MB")
        sb.appendLine("  可用内存: ${runtime.freeMemory() / 1024 / 1024}MB")
        sb.appendLine("  最大内存: ${runtime.maxMemory() / 1024 / 1024}MB")
        sb.appendLine("  已用内存: ${(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024}MB")
        sb.appendLine()
        sb.appendLine("系统信息:")
        sb.appendLine("  CPU核心数: ${Runtime.getRuntime().availableProcessors()}")
        sb.appendLine("  JVM版本: ${System.getProperty("java.version")}")
        sb.appendLine("  JVM厂商: ${System.getProperty("java.vendor")}")
        sb.appendLine()
        sb.appendLine("构建配置:")
        sb.appendLine("  Gradle版本: ${gradle.gradleVersion}")
        sb.appendLine("  工作线程数: ${System.getProperty("org.gradle.workers.max", "auto")}")
        sb.appendLine("  并行构建: ${System.getProperty("org.gradle.parallel", "false")}")
        sb.appendLine("  构建缓存: ${System.getProperty("org.gradle.caching", "false")}")
        sb.appendLine()
        sb.appendLine("=== 报告结束 ===")

        reportFile.writeText(sb.toString())
        println("📄 内存报告已生成: ${reportFile.absolutePath}")
    }
}

// ========== 容器化集成任务 ==========

tasks.register<Exec>("podmanBuild") {
    group = "distribution"
    description = "使用 Podman 构建项目的容器镜像"
    dependsOn("fatJar") // 确保先构建出最新的 JAR

    commandLine("podman", "build", "-t", "cloudsim-benchmark:latest", "-f", "Containerfile", ".")

    doFirst {
        logger.lifecycle("🚀 正在构建 Podman 镜像: cloudsim-benchmark:latest...")
    }
}

tasks.register<Exec>("podmanRunHelp") {
    group = "distribution"
    description = "在容器中运行帮助命令"
    commandLine("podman", "run", "--rm", "cloudsim-benchmark:latest", "--help")
}
