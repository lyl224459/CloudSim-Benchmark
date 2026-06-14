package buildlogic

import org.gradle.api.GradleException
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

internal object CloudSimPlusJarSanitizer {
    fun sanitizeRepository(
        rawMavenRepo: File,
        sanitizedMavenRepo: File,
        artifactGroup: String,
        artifactName: String,
    ): List<File> {
        if (!rawMavenRepo.isDirectory) {
            throw GradleException("Raw CloudSim Plus Maven repo not found: ${rawMavenRepo.path}")
        }
        sanitizedMavenRepo.deleteRecursively()
        rawMavenRepo.copyRecursively(sanitizedMavenRepo, overwrite = true)

        val runtimeJars = runtimeJars(sanitizedMavenRepo, artifactGroup, artifactName)
        if (runtimeJars.isEmpty()) {
            val artifactRoot = artifactRoot(sanitizedMavenRepo, artifactGroup, artifactName)
            throw GradleException("No CloudSim Plus runtime jar found in ${artifactRoot.path}")
        }
        runtimeJars.forEach(::sanitizeJar)
        return runtimeJars
    }

    fun runtimeJars(
        mavenRepo: File,
        artifactGroup: String,
        artifactName: String,
    ): List<File> {
        val artifactRoot = artifactRoot(mavenRepo, artifactGroup, artifactName)
        if (!artifactRoot.isDirectory) {
            return emptyList()
        }
        return artifactRoot
            .walkTopDown()
            .filter { file -> isRuntimeJar(file, artifactName) }
            .toList()
    }

    fun sanitizeJar(jar: File): Boolean {
        val tempJar = jar.resolveSibling("${jar.name}.tmp")
        JarFile(jar).use { input ->
            val manifest = input.manifest ?: Manifest()
            if (manifest.mainAttributes.remove(Attributes.Name.CLASS_PATH) == null) {
                return false
            }
            JarOutputStream(tempJar.outputStream().buffered(), manifest).use { output ->
                input.entries().asSequence().forEach { entry ->
                    if (entry.name.equals(JarFile.MANIFEST_NAME, ignoreCase = true)) {
                        return@forEach
                    }
                    output.putNextEntry(entry.copyWithoutStream())
                    if (!entry.isDirectory) {
                        input.getInputStream(entry).use { stream ->
                            stream.copyTo(output)
                        }
                    }
                    output.closeEntry()
                }
            }
        }
        replaceJar(jar, tempJar)
        return true
    }

    private fun artifactRoot(
        mavenRepo: File,
        artifactGroup: String,
        artifactName: String,
    ): File = mavenRepo.resolve("${artifactGroup.replace('.', File.separatorChar)}${File.separator}$artifactName")

    private fun isRuntimeJar(
        file: File,
        artifactName: String,
    ): Boolean =
        file.isFile &&
            file.extension == "jar" &&
            file.name.startsWith("$artifactName-") &&
            !file.name.endsWith("-sources.jar") &&
            !file.name.endsWith("-javadoc.jar")

    private fun JarEntry.copyWithoutStream(): JarEntry =
        JarEntry(name).also { copy ->
            copy.time = time
            copy.comment = comment
            copy.extra = extra
        }

    private fun replaceJar(
        jar: File,
        tempJar: File,
    ) {
        if (!jar.delete()) {
            tempJar.delete()
            throw GradleException("Failed to replace CloudSim Plus jar: ${jar.path}")
        }
        if (!tempJar.renameTo(jar)) {
            throw GradleException("Failed to move sanitized CloudSim Plus jar into place: ${jar.path}")
        }
    }
}
