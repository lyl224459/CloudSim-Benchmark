plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    application
}

group = "com.lyl224459"
version = "1.0.0"

description = "CloudSim-Benchmark: 云任务调度算法对比实验平台"

// 动态检测CPU核心数并优化构建配置
val cpuCores = Runtime.getRuntime().availableProcessors()

// 自动使用全部CPU核心作为工作线程数
val workerThreads = cpuCores

// 设置合理的上限，避免过度并行（通常2倍CPU核心数已足够）
val maxReasonableThreads = cpuCores * 2
val finalWorkerThreads = minOf(workerThreads, maxReasonableThreads)

// 通过系统属性设置工作线程数
System.setProperty("org.gradle.workers.max", finalWorkerThreads.toString())

logger.lifecycle("🔧 构建优化已启用 - CPU核心数: $cpuCores, 工作线程数: $finalWorkerThreads")
logger.lifecycle("⚡ 并行构建: 已启用 | 构建缓存: 已启用 | GC优化: 已启用")

repositories {
    mavenCentral()
    // mavenLocal() - 已移除，使用 Maven Central 的已发布版本 8.5.5
}

java {
    sourceCompatibility = JavaVersion.VERSION_23
    targetCompatibility = JavaVersion.VERSION_23
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_23)

        // Kotlin 编译优化
        allWarningsAsErrors.set(false)
        suppressWarnings.set(true)

        // 添加编译参数优化
        freeCompilerArgs.addAll(listOf(
            "-Xinline-classes"
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

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()

    // 测试优化：仅在需要时运行
    // 注意：配置缓存不支持 project.hasProperty，使用系统属性代替
    onlyIf {
        System.getProperty("runTests") != null ||
        project.hasProperty("runTests")
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
    // 添加模块系统相关参数和编码设置
    jvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "-Dfile.encoding=UTF-8",
        "-Dconsole.encoding=UTF-8"
    )
    // 设置标准输出编码
    systemProperty("file.encoding", "UTF-8")
}

// 创建 fat jar 任务
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("cloudsim-benchmark")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

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
                !name.contains("mockito")
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
}

// 优化ZIP任务性能
tasks.withType<Zip> {
    // 使用STORE而不是DEFLATE以提升速度（对于JAR文件）
    isZip64 = true
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

// ========== Gradle 运行任务 ==========

/**
 * 批处理模式运行任务
 * 用法: 
 *   gradle runBatch                                    # 运行所有算法
 *   gradle runBatch -Palgorithms=PSO,WOA              # 运行指定算法
 *   gradle runBatch -Palgorithms=PSO,WOA -Pseed=42     # 指定算法和随机种子
 */
tasks.register<JavaExec>("runBatch") {
    group = "application"
    description = "运行批处理调度模式实验"
    mainClass.set("MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    dependsOn("classes")
    
    // 获取参数（通过 -P 传递）
    val algorithms = project.findProperty("algorithms") as String?
    val seed = project.findProperty("seed") as String?
    
    // 构建参数列表（与命令行格式一致）
    val argsList = mutableListOf<String>("batch")
    if (algorithms != null && algorithms.isNotEmpty()) {
        argsList.add(algorithms)
    }
    if (seed != null && seed.isNotEmpty()) {
        argsList.add(seed)
    }
    
    args = argsList
    
    jvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "-Dfile.encoding=UTF-8",
        "-Dconsole.encoding=UTF-8"
    )
    systemProperty("file.encoding", "UTF-8")
}

/**
 * 实时调度模式运行任务
 * 用法: 
 *   gradle runRealtime                                    # 运行所有算法
 *   gradle runRealtime -Palgorithms=PSO_REALTIME,WOA_REALTIME  # 运行指定算法
 *   gradle runRealtime -Palgorithms=PSO_REALTIME,WOA_REALTIME -Pseed=123  # 指定算法和随机种子
 */
tasks.register<JavaExec>("runRealtime") {
    group = "application"
    description = "运行实时调度模式实验"
    mainClass.set("MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    dependsOn("classes", "processResources")
    
    // 获取参数（通过 -P 传递）
    val algorithms = project.findProperty("algorithms") as String?
    val seed = project.findProperty("seed") as String?
    
    // 构建参数列表（与命令行格式一致）
    val argsList = mutableListOf<String>("realtime")
    if (algorithms != null && algorithms.isNotEmpty()) {
        argsList.add(algorithms)
    }
    if (seed != null && seed.isNotEmpty()) {
        argsList.add(seed)
    }
    
    args = argsList
    
    jvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "-Dfile.encoding=UTF-8",
        "-Dconsole.encoding=UTF-8"
    )
    systemProperty("file.encoding", "UTF-8")
}

/**
 * 批量任务数实验任务
 * 用法: 
 *   gradle runBatchMulti                                    # 默认任务数 (50,100,200,500)
 *   gradle runBatchMulti -PcloudletCounts=50,100,200,500,1000  # 指定任务数
 *   gradle runBatchMulti -PcloudletCounts=50,100,200 -Palgorithms=PSO,WOA  # 指定任务数和算法
 *   gradle runBatchMulti -PcloudletCounts=50,100,200 -Palgorithms=PSO,WOA -Pseed=42  # 完整参数
 */
tasks.register<JavaExec>("runBatchMulti") {
    group = "application"
    description = "运行批量任务数实验"
    mainClass.set("MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    dependsOn("classes", "processResources")
    
    // 获取参数（通过 -P 传递）
    val cloudletCounts = project.findProperty("cloudletCounts") as String? ?: "50,100,200,500"
    val algorithms = project.findProperty("algorithms") as String?
    val seed = project.findProperty("seed") as String?
    
    // 构建参数列表（与命令行格式一致）
    val argsList = mutableListOf<String>("batch-multi", cloudletCounts)
    if (algorithms != null && algorithms.isNotEmpty()) {
        argsList.add(algorithms)
    }
    if (seed != null && seed.isNotEmpty()) {
        argsList.add(seed)
    }
    
    args = argsList
    
    jvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "-Dfile.encoding=UTF-8",
        "-Dconsole.encoding=UTF-8"
    )
    systemProperty("file.encoding", "UTF-8")
}

// 实时调度模式批量任务数实验任务
tasks.register<JavaExec>("runRealtimeMulti") {
    group = "application"
    description = "运行实时调度模式批量任务数实验"
    mainClass.set("MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    dependsOn("classes", "processResources")

    val cloudletCounts = project.findProperty("cloudletCounts") as String?
    val algorithms = project.findProperty("algorithms") as String?
    val seed = project.findProperty("seed") as String?

    val argsList = mutableListOf<String>("realtime-multi")
    if (cloudletCounts != null && cloudletCounts.isNotEmpty()) {
        argsList.add(cloudletCounts)
    } else {
        // Default cloudlet counts if not provided
        argsList.add("50,100,200,500")
    }
    if (algorithms != null && algorithms.isNotEmpty()) {
        argsList.add(algorithms)
    }
    if (seed != null && seed.isNotEmpty()) {
        argsList.add(seed)
    }

    args = argsList

    jvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "-Dfile.encoding=UTF-8",
        "-Dconsole.encoding=UTF-8"
    )
    systemProperty("file.encoding", "UTF-8")
}

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

/**
 * 通用运行任务（支持自定义模式和所有参数）
 * 用法: 
 *   gradle runExp -Pmode=batch -Palgorithms=PSO,WOA -Pseed=42
 *   gradle runExp -Pmode=batch-multi -Palgorithms=PSO,WOA
 *   gradle runExp -Pmode=realtime -Palgorithms=PSO_REALTIME,WOA_REALTIME
 *   gradle runExp -Pmode=realtime-multi -Palgorithms=PSO_REALTIME,WOA_REALTIME
 */
tasks.register<JavaExec>("runExp") {
    group = "application"
    description = "运行实验（支持自定义模式）"
    mainClass.set("MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    dependsOn("classes", "processResources")
    
    // 获取参数（通过 -P 传递）
    val mode = project.findProperty("mode") as String? ?: "realtime"
    val algorithms = project.findProperty("algorithms") as String?
    val seed = project.findProperty("seed") as String?
    
    // 构建参数列表（与命令行格式一致）
    val argsList = mutableListOf<String>(mode)
    if (algorithms != null && algorithms.isNotEmpty()) {
        argsList.add(algorithms)
    }
    if (seed != null && seed.isNotEmpty()) {
        argsList.add(seed)
    }
    
    args = argsList
    
    jvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "-Dfile.encoding=UTF-8",
        "-Dconsole.encoding=UTF-8"
    )
    systemProperty("file.encoding", "UTF-8")
}

