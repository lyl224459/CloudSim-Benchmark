import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    `kotlin-dsl`
    jacoco
}

gradlePlugin {
    plugins {
        create("buildLogicTestFixture") {
            id = "cloudsim-benchmark.buildlogic-test-fixture"
            implementationClass = "buildlogic.BuildLogicTestFixturePlugin"
        }
    }
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

val testKitJacocoAgent by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("25"))
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation(gradleTestKit())
    testKitJacocoAgent("org.jacoco:org.jacoco.agent:${jacoco.toolVersion}:runtime")
}

val testKitJacocoDirectory = layout.buildDirectory.dir("jacoco/testkit")
val testKitJacocoPath = testKitJacocoDirectory.get().asFile.absolutePath
val jacocoAgentPath = testKitJacocoAgent.singleFile.absolutePath

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
    systemProperty("buildlogic.testkit.jacocoAgent", jacocoAgentPath)
    systemProperty("buildlogic.testkit.jacocoDirectory", testKitJacocoPath)
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    executionData(fileTree(testKitJacocoDirectory) { include("*.exec") })
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    executionData(fileTree(testKitJacocoDirectory) { include("*.exec") })
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.65".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn("jacocoTestReport", "jacocoTestCoverageVerification")
}
