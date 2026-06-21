package buildlogic

import java.io.File

internal object ContainerImageSmokeSupport {
    fun buildCommand(
        containerExecutable: String,
        imageName: String,
        containerFile: File,
    ): List<String> =
        listOf(containerExecutable, "build", "-t", imageName, "-f", containerFile.absolutePath, ".")

    fun runCommand(
        containerExecutable: String,
        imageName: String,
    ): List<String> =
        listOf(
            containerExecutable,
            "run",
            "--rm",
            "--read-only",
            "--tmpfs",
            "/tmp:rw,nosuid,nodev",
            "--mount",
            runsTmpfsMount(),
            imageName,
            "--help",
        )

    fun inspectCommand(
        containerExecutable: String,
        imageName: String,
    ): List<String> =
        listOf(
            containerExecutable,
            "run",
            "--rm",
            "--read-only",
            "--tmpfs",
            "/tmp:rw,nosuid,nodev",
            "--mount",
            runsTmpfsMount(),
            "--entrypoint",
            "sh",
            imageName,
            "-c",
            "test \"$(id -u)\" = 10001 && touch /app/runs/smoke && " +
                "test ! -e /app/.git && test ! -e /app/.gradle && " +
                "test ! -e /app/src && test ! -e /app/buildSrc && test ! -e /app/gradlew",
        )

    fun missingContainerRuntimeMessage(
        containerExecutable: String,
        ci: Boolean,
    ): String =
        if (ci) {
            "Container runtime '$containerExecutable' is required for containerImageSmoke in CI."
        } else {
            "Container runtime '$containerExecutable' was not found; skipping local containerImageSmoke."
        }

    private fun runsTmpfsMount(): String =
        "type=tmpfs,destination=/app/runs,tmpfs-mode=1777"
}
