package buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ContainerImageSmokeSupportTest {
    @Test
    fun `build and run commands use explicit docker arguments`() {
        val containerFile = File("Containerfile")

        assertEquals(
            listOf("docker", "build", "-t", "cloudsim-benchmark:smoke", "-f", containerFile.absolutePath, "."),
            ContainerImageSmokeSupport.buildCommand(
                dockerExecutable = "docker",
                imageName = "cloudsim-benchmark:smoke",
                containerFile = containerFile,
            ),
        )
        assertEquals(
            listOf(
                "docker",
                "run",
                "--rm",
                "--read-only",
                "--tmpfs",
                "/tmp:rw,nosuid,nodev",
                "--mount",
                "type=tmpfs,destination=/app/runs,tmpfs-mode=1777",
                "cloudsim-benchmark:smoke",
                "--help",
            ),
            ContainerImageSmokeSupport.runCommand(
                dockerExecutable = "docker",
                imageName = "cloudsim-benchmark:smoke",
            ),
        )
        assertContains(
            ContainerImageSmokeSupport
                .inspectCommand("docker", "cloudsim-benchmark:smoke")
                .joinToString(" "),
            "test ! -e /app/.git",
        )
    }

    @Test
    fun `buildx command enables github actions cache`() {
        val containerFile = File("Containerfile")

        assertEquals(
            listOf(
                "docker",
                "buildx",
                "build",
                "--load",
                "--cache-from=type=gha,scope=container-smoke",
                "--cache-to=type=gha,mode=max,scope=container-smoke",
                "-t",
                "cloudsim-benchmark:smoke",
                "-f",
                containerFile.absolutePath,
                ".",
            ),
            ContainerImageSmokeSupport.buildCommand(
                dockerExecutable = "docker",
                imageName = "cloudsim-benchmark:smoke",
                containerFile = containerFile,
                useBuildx = true,
                useGitHubActionsCache = true,
            ),
        )
    }

    @Test
    fun `missing docker message distinguishes ci from local runs`() {
        assertEquals(
            "Docker executable 'docker' is required for containerImageSmoke in CI.",
            ContainerImageSmokeSupport.missingDockerMessage("docker", ci = true),
        )
        assertEquals(
            "Docker executable 'docker' was not found; skipping local containerImageSmoke.",
            ContainerImageSmokeSupport.missingDockerMessage("docker", ci = false),
        )
    }
}
