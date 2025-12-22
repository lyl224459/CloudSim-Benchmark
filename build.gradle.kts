plugins {
    kotlin("jvm") version "2.1.21"
    application
}

group = "com.lyl224459"
version = "1.0.0"

description = "CloudSim-Benchmark: 云任务调度算法对比实验平台"

repositories {
    mavenLocal()
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_23
    targetCompatibility = JavaVersion.VERSION_23
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_23)
    }
}

dependencies {
    implementation("org.cloudsimplus:cloudsimplus:9.0.0-SNAPSHOT")
    implementation("org.apache.commons:commons-math3:3.6.1")
    
    // 日志库：kotlin-logging (Kotlin友好的日志API)
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    
    // 日志实现：slf4j + logback
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
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
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
}

// 创建运行脚本，设置正确的编码
tasks.register<CreateStartScripts>("createRunScript") {
    applicationName = "run-comparison"
    mainClass.set("MainKt")
    outputDir = file("$buildDir/scripts")
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
 * 通用运行任务（支持自定义模式和所有参数）
 * 用法: 
 *   gradle runExp -Pmode=batch -Palgorithms=PSO,WOA -Pseed=42
 *   gradle runExp -Pmode=realtime -Palgorithms=PSO_REALTIME,WOA_REALTIME
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

