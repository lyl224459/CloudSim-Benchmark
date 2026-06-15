package buildlogic

import java.io.File
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarFile

internal object CloudSimPlusClasspathVerifier {
    fun verifyClasspath(
        configurationName: String,
        files: Set<File>,
        localRepoRoot: Path,
        artifactName: String,
    ) {
        val canonicalRepoRoot = localRepoRoot.toFile().canonicalFile.toPath()
        val cloudSimJars =
            files.filter { file ->
                file.name.startsWith("$artifactName-") && file.extension == "jar"
            }
        check(cloudSimJars.isNotEmpty()) {
            "No CloudSim Plus jar found in $configurationName"
        }
        cloudSimJars.forEach { jar ->
            check(jar.canonicalFile.toPath().startsWith(canonicalRepoRoot)) {
                "CloudSim Plus jar for $configurationName is not from source build repo: ${jar.path}"
            }
            JarFile(jar).use { jarFile ->
                check(
                    jarFile.manifest
                        ?.mainAttributes
                        ?.getValue(Attributes.Name.CLASS_PATH)
                        .isNullOrBlank(),
                ) {
                    "CloudSim Plus jar for $configurationName still has manifest Class-Path: ${jar.path}"
                }
            }
        }
    }
}
