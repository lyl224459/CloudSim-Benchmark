package buildlogic

import java.io.File

internal object ContainerImageSmokeSupport {
    fun buildCommand(
        dockerExecutable: String,
        imageName: String,
        containerFile: File,
        useBuildx: Boolean = false,
        useGitHubActionsCache: Boolean = false,
    ): List<String> =
        buildList {
            add(dockerExecutable)
            if (useBuildx) {
                addAll(listOf("buildx", "build", "--load"))
                if (useGitHubActionsCache) {
                    addAll(
                        listOf(
                            "--cache-from=type=gha,scope=container-smoke",
                            "--cache-to=type=gha,mode=max,scope=container-smoke",
                        ),
                    )
                }
            } else {
                add("build")
            }
            addAll(listOf("-t", imageName, "-f", containerFile.absolutePath, "."))
        }

    fun runCommand(
        dockerExecutable: String,
        imageName: String,
    ): List<String> =
        listOf(
            dockerExecutable,
            "run",
            "--rm",
            "--read-only",
            "--tmpfs",
            "/tmp:rw,nosuid,nodev",
            "--tmpfs",
            "/app/runs:rw,nosuid,nodev",
            imageName,
            "--help",
        )

    fun inspectCommand(
        dockerExecutable: String,
        imageName: String,
    ): List<String> =
        listOf(
            dockerExecutable,
            "run",
            "--rm",
            "--read-only",
            "--tmpfs",
            "/tmp:rw,nosuid,nodev",
            "--tmpfs",
            "/app/runs:rw,nosuid,nodev",
            "--entrypoint",
            "sh",
            imageName,
            "-c",
            "test \"$(id -u)\" = 10001 && touch /app/runs/smoke && " +
                "test ! -e /app/.git && test ! -e /app/.gradle && " +
                "test ! -e /app/src && test ! -e /app/buildSrc && test ! -e /app/gradlew",
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
