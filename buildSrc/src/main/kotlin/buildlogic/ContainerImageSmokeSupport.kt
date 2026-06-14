package buildlogic

import java.io.File

internal object ContainerImageSmokeSupport {
    fun buildCommand(
        dockerExecutable: String,
        imageName: String,
        containerFile: File,
    ): List<String> =
        listOf(
            dockerExecutable,
            "build",
            "-t",
            imageName,
            "-f",
            containerFile.absolutePath,
            ".",
        )

    fun runCommand(
        dockerExecutable: String,
        imageName: String,
    ): List<String> =
        listOf(
            dockerExecutable,
            "run",
            "--rm",
            imageName,
            "--help",
        )

    fun missingDockerMessage(
        dockerExecutable: String,
        ci: Boolean,
    ): String =
        if (ci) {
            "Docker executable '$dockerExecutable' is required for containerImageSmoke in CI."
        } else {
            "Docker executable '$dockerExecutable' was not found; skipping local containerImageSmoke."
        }
}
