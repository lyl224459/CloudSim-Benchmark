package buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class JUnitVerificationTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `signature and inventory tasks pass and reuse configuration cache`() {
        val fixture = fixture("junit-verification-success")
        writeTestSources(fixture, "public void works() {}")
        fixture.resolve("inventory.lock").writeText(inventory("fixture.SampleTest#works()"))
        fixture.writeBuild(verificationTasks())

        val result = fixture.run("verifySignatures", "verifyInventory")

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifySignatures")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyInventory")?.outcome)
        fixture.run("verifySignatures", "verifyInventory", "--configuration-cache")
        val reused = fixture.run("verifySignatures", "verifyInventory", "--configuration-cache")
        assertContains(reused.output, "Reusing configuration cache")
    }

    @Test
    fun `signature task rejects non void test methods`() {
        val fixture = fixture("junit-signature-failure")
        writeTestSources(fixture, "public String works() { return \"bad\"; }")
        fixture.resolve("inventory.lock").writeText(inventory("fixture.SampleTest#works()"))
        fixture.writeBuild(verificationTasks())

        val output = fixture.runAndFail("verifySignatures").output

        assertContains(output, "must return void/Unit")
        assertContains(output, "fixture.SampleTest#works returns java.lang.String")
    }

    @Test
    fun `inventory task reports changed and missing inventories`() {
        val changed = fixture("junit-inventory-changed")
        writeTestSources(changed, "public void works() {}")
        changed.resolve("inventory.lock").writeText(inventory("fixture.SampleTest#oldName()"))
        changed.writeBuild(verificationTasks())
        assertContains(changed.runAndFail("verifyInventory").output, "JUnit test inventory changed")

        val missing = fixture("junit-inventory-missing")
        writeTestSources(missing, "public void works() {}")
        missing.writeBuild(verificationTasks())
        assertContains(missing.runAndFail("verifyInventory").output, "inventory.lock")
    }

    private fun writeTestSources(
        fixture: GradleTaskFixture,
        method: String,
    ) {
        fixture.writeJava(
            "src/test/java/org/junit/platform/commons/annotation/Testable.java",
            """
            package org.junit.platform.commons.annotation;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
            public @interface Testable {}
            """,
        )
        fixture.writeJava(
            "src/test/java/org/junit/jupiter/api/Test.java",
            """
            package org.junit.jupiter.api;
            import java.lang.annotation.*;
            import org.junit.platform.commons.annotation.Testable;
            @Testable
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
            public @interface Test {}
            """,
        )
        fixture.writeJava(
            "src/test/java/fixture/SampleTest.java",
            """
            package fixture;
            import org.junit.jupiter.api.Test;
            public class SampleTest {
                @Test
                $method
            }
            """,
        )
    }

    private fun verificationTasks(): String =
        """
        plugins {
            id 'java'
        }

        import buildlogic.VerifyJUnitTestInventoryTask
        import buildlogic.VerifyJUnitTestSignaturesTask

        tasks.register('verifySignatures', VerifyJUnitTestSignaturesTask) {
            dependsOn testClasses
            testClassesDirs.from(sourceSets.test.output.classesDirs)
            testRuntimeClasspath.from(sourceSets.test.runtimeClasspath)
        }
        tasks.register('verifyInventory', VerifyJUnitTestInventoryTask) {
            dependsOn testClasses
            testClassesDirs.from(sourceSets.test.output.classesDirs)
            testRuntimeClasspath.from(sourceSets.test.runtimeClasspath)
            inventoryFile.set(layout.projectDirectory.file('inventory.lock'))
        }
        """

    private fun inventory(entry: String): String = "format=1\ncount=1\nentry=$entry\n"

    private fun GradleTaskFixture.writeJava(
        path: String,
        content: String,
    ) {
        resolve(path).also { it.parentFile.mkdirs() }.writeText(content.trimIndent())
    }

    private fun fixture(name: String) = GradleTaskFixture(tempDir.resolve(name))
}
