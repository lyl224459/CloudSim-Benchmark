package buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CloudSimPlusVerifySourceBuildTaskFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `source build verification accepts local artifacts and rejects drift`() {
        val fixture = GradleTaskFixture(tempDir.resolve("verify-source-build"))
        val repo = fixture.resolve("repo")
        val localJar = repo.resolve("org/cloudsimplus/cloudsimplus/8.5.7/cloudsimplus-8.5.7.jar")
        createPlainJar(localJar)
        fixture.writeBuild(taskScript(repo, localJar))

        val success = fixture.run("verifySourceBuild")
        assertEquals(TaskOutcome.SUCCESS, success.task(":verifySourceBuild")?.outcome)

        val externalJar = fixture.resolve("external/cloudsimplus-8.5.7.jar")
        createPlainJar(externalJar)
        fixture.writeBuild(taskScript(repo, externalJar))

        assertContains(fixture.runAndFail("verifySourceBuild").output, "is not from source build repo")
    }

    private fun taskScript(
        repo: File,
        artifact: File,
    ): String =
        """
        import buildlogic.VerifyCloudSimPlusSourceBuildTask

        tasks.register('verifySourceBuild', VerifyCloudSimPlusSourceBuildTask) {
            localMavenRepo.set(layout.projectDirectory.dir('${repo.relativeTo(tempDir.resolve("verify-source-build")).path.gradlePath()}'))
            artifactName.set('cloudsimplus')
            compileClasspath.from(files('${artifact.absolutePath.gradlePath()}'))
            runtimeClasspath.from(files('${artifact.absolutePath.gradlePath()}'))
            testRuntimeClasspath.from(files('${artifact.absolutePath.gradlePath()}'))
        }
        """
}
