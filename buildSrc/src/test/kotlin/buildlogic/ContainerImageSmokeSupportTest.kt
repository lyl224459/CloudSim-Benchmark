package buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ContainerImageSmokeSupportTest {
    @Test
    fun `build and run commands use explicit podman arguments`() {
        val containerFile = File("Containerfile")

        assertEquals(
            listOf("podman", "build", "-t", "cloudsim-benchmark:smoke", "-f", containerFile.absolutePath, "."),
            ContainerImageSmokeSupport.buildCommand(
                containerExecutable = "podman",
                imageName = "cloudsim-benchmark:smoke",
                containerFile = containerFile,
            ),
        )
        assertEquals(
            listOf(
                "podman",
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
                containerExecutable = "podman",
                imageName = "cloudsim-benchmark:smoke",
            ),
        )
        assertContains(
            ContainerImageSmokeSupport
                .inspectCommand("podman", "cloudsim-benchmark:smoke")
                .joinToString(" "),
            "test ! -e /app/.git",
        )
    }

    @Test
    fun `missing container runtime message distinguishes ci from local runs`() {
        assertEquals(
            "Container runtime 'podman' is required for containerImageSmoke in CI.",
            ContainerImageSmokeSupport.missingContainerRuntimeMessage("podman", ci = true),
        )
        assertEquals(
            "Container runtime 'podman' was not found; skipping local containerImageSmoke.",
            ContainerImageSmokeSupport.missingContainerRuntimeMessage("podman", ci = false),
        )
    }
}
