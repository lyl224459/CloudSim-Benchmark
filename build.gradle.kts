plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    application
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


repositories {
    mavenCentral()
    // mavenLocal() - 已移除，使用 Maven Central 的已发布版本 8.5.5
}

java {
    sourceCompatibility = JavaVersion.VERSION_23
    targetCompatibility = JavaVersion.VERSION_23
}

val cloudSimJvmArgs = listOf(
    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
    "--add-opens", "java.base/java.util=ALL-UNNAMED",
    "--add-opens", "java.base/java.nio=ALL-UNNAMED",
    "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
    "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
    "--enable-native-access=ALL-UNNAMED",
    "-Dfile.encoding=UTF-8",
    "-Dconsole.encoding=UTF-8",
    "-XX:+UseZGC"
)

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_23)

        // Kotlin 编译优化
        allWarningsAsErrors.set(false)
        suppressWarnings.set(true)

        // 添加编译参数优化
        freeCompilerArgs.addAll(listOf(
            "-Xinline-classes",
            "-Xbackend-threads=$cpuCores",
            "-Xlambdas=indy"
        ))
    }
}

dependencies {
    implementation("org.cloudsimplus:cloudsimplus:8.5.5")
    implementation("org.apache.commons:commons-math3:3.6.1")

    // 日志库：kotlin-logging (Kotlin友好的日志API)
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // 日志实现：slf4j + logback
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // TOML配置文件解析库
    implementation("com.akuleshov7:ktoml-core:0.5.0")
    implementation("com.akuleshov7:ktoml-file:0.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Kotlin协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // 高性能计算库
    implementation("it.unimi.dsi:fastutil:8.5.12")            // Fastutil - 高性能集合

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

tasks.test {
    val runTestsRequested = providers.systemProperty("runTests").isPresent ||
        providers.gradleProperty("runTests").isPresent
    useJUnitPlatform()

    // 测试优化：仅在需要时运行
    onlyIf { runTestsRequested }

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

// 创建测试覆盖率任务
tasks.register("testWithCoverage") {
    dependsOn("test")
    doLast {
        logger.lifecycle("🧪 测试完成 - 查看 reports/tests/test/index.html 获取详细报告")
    }
}


application {
    mainClass.set("MainKt")
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
val forceJarCompressionProvider = providers.gradleProperty("compress").map { it == "true" }.orElse(false)
val skipJarCompressionProvider = providers.gradleProperty("skipCompress").map { true }.orElse(false)
val fastBuildProvider = providers.gradleProperty("fast").map { true }.orElse(false)

// 创建 fat jar 任务
tasks.register<Jar>("fatJar") {
    val compressedEntries = (isCiBuildProvider.get() || forceJarCompressionProvider.get()) &&
        !(skipJarCompressionProvider.get() || fastBuildProvider.get())
    archiveBaseName.set("cloudsim-benchmark")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    entryCompression = if (compressedEntries) ZipEntryCompression.DEFLATED else ZipEntryCompression.STORED

    // 优化：只包含必要的类文件
    from(sourceSets.main.get().output)

    // 依赖处理优化
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
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
            }
            .map { zipTree(it) }
    })

    manifest {
        attributes["Main-Class"] = "MainKt"
        // 添加优化标志
        attributes["Implementation-Version"] = project.version
    }

    // 排除不必要的文件以减少大小
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    
    // 确保在 CI/发布环境中使用压缩（继承自 tasks.withType<Zip> 的配置）
    doFirst {
        if (compressedEntries) {
            logger.lifecycle("📦 发布模式：fatJar 启用压缩（减小体积）")
        } else {
            logger.lifecycle("⚡ 开发模式：fatJar 禁用压缩（提升构建速度）")
        }
    }
}

// 优化 Zip 任务性能 - 根据环境自动选择压缩策略
tasks.withType<Zip> {
    val compressedEntries = (isCiBuildProvider.get() || forceJarCompressionProvider.get()) &&
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
        val criticalFiles = listOf(
            "src/main/kotlin/Main.kt",
            "build.gradle.kts",
            "gradle.properties"
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
    outputDir = layout.buildDirectory.dir("scripts").get().asFile
    classpath = tasks.jar.get().outputs.files + configurations.runtimeClasspath.get()
    
    doLast {
        val windowsScript = file("$outputDir/${applicationName}.bat")
        if (windowsScript.exists()) {
            val content = windowsScript.readText()
            windowsScript.writeText(
                "@echo off\n" +
                "chcp 65001 >nul\n" +
                content.replace(
                    "set DEFAULT_JVM_OPTS=",
                    "set DEFAULT_JVM_OPTS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 "
                )
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
    defaultTasks: String? = null
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
        val dryRun = providers.gradleProperty("dryRun").map(String::toBoolean).orElse(false).get()

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
    defaultTasks = "50,100,200,500"
)

// 实时调度模式批量任务数实验任务
registerCloudSimRunTask(
    "runRealtimeMulti",
    "realtime-multi",
    "运行实时调度模式批量任务数实验",
    defaultTasks = "50,100,200,500"
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
        val reportFile = layout.buildDirectory.file("reports/memory-report.txt").get().asFile
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
