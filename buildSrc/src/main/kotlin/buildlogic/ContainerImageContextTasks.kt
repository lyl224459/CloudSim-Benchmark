package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

@CacheableTask
abstract class PrepareContainerImageContextTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val executableJar: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configsDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeContainerFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val cloudSimPlusMetadataFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        executableJar.get().asFile.copyTo(output.resolve(APP_JAR), overwrite = true)
        runtimeContainerFile.get().asFile.copyTo(output.resolve(CONTAINER_FILE), overwrite = true)
        configsDirectory.get().asFile.copyRecursively(output.resolve(CONFIGS_DIR), overwrite = true)
        dataDirectory.get().asFile.copyRecursively(output.resolve(DATA_DIR), overwrite = true)

        val metadata = CloudSimPlusLockSupport.read(cloudSimPlusMetadataFile.get().asFile)
        val contextFiles = contextFiles(output)
        output.resolve(PROVENANCE_FILE).writeText(
            ContainerImageContextProvenance.render(metadata, output.resolve(APP_JAR), contextFiles),
        )
    }
}

@CacheableTask
abstract class VerifyContainerImageContextTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contextDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val cloudSimPlusMetadataFile: RegularFileProperty

    @get:Input
    abstract val maximumBytes: Property<Long>

    @TaskAction
    fun verify() {
        val context = contextDirectory.get().asFile
        val files = contextFiles(context)
        val totalBytes = files.sumOf(File::length)
        if (totalBytes > maximumBytes.get()) {
            throw GradleException(
                "Container build context is ${totalBytes} bytes, exceeding ${maximumBytes.get()} bytes.",
            )
        }

        val prohibited = files.filter { file -> isProhibited(file.relativeTo(context).invariantSeparatorsPath) }
        if (prohibited.isNotEmpty()) {
            throw GradleException(
                "Container build context contains prohibited files: " +
                    prohibited.joinToString { it.relativeTo(context).invariantSeparatorsPath },
            )
        }

        val expectedMetadata = CloudSimPlusLockSupport.read(cloudSimPlusMetadataFile.get().asFile)
        ContainerImageContextProvenance.verify(context, expectedMetadata)
        logger.lifecycle("Container build context verified: ${files.size} files, $totalBytes bytes")
    }

    private fun isProhibited(path: String): Boolean {
        val segments = path.split("/")
        return segments.any { it in PROHIBITED_SEGMENTS } ||
            path.endsWith(".kt") ||
            path.endsWith(".kts") ||
            path.endsWith(".java") ||
            path.endsWith(".gradle") ||
            path.startsWith("gradlew")
    }
}

internal object ContainerImageContextProvenance {
    private const val FORMAT = "1"

    fun render(
        metadata: CloudSimPlusMetadata,
        executableJar: File,
        files: List<File>,
    ): String =
        buildString {
            appendLine("format=$FORMAT")
            appendLine("cloudsimplus.ref=${metadata.ref}")
            appendLine("cloudsimplus.commit=${metadata.commit}")
            appendLine("cloudsimplus.version=${metadata.version}")
            appendLine("fatJar.sha256=${sha256(executableJar)}")
            files
                .map { it.relativeTo(executableJar.parentFile).invariantSeparatorsPath }
                .filterNot { it == PROVENANCE_FILE }
                .sorted()
                .forEach { appendLine("file=$it") }
        }

    fun verify(
        context: File,
        expectedMetadata: CloudSimPlusMetadata,
    ) {
        val provenanceFile = context.resolve(PROVENANCE_FILE)
        if (!provenanceFile.isFile) {
            throw GradleException("Missing container provenance: ${provenanceFile.path}")
        }
        val entries =
            provenanceFile
                .readLines()
                .filter(String::isNotBlank)
                .map { line -> line.substringBefore("=") to line.substringAfter("=", "") }
        val scalar = entries.filterNot { it.first == "file" }.toMap()
        val expectedScalar =
            mapOf(
                "format" to FORMAT,
                "cloudsimplus.ref" to expectedMetadata.ref,
                "cloudsimplus.commit" to expectedMetadata.commit,
                "cloudsimplus.version" to expectedMetadata.version,
                "fatJar.sha256" to sha256(context.resolve(APP_JAR)),
            )
        if (scalar != expectedScalar) {
            throw GradleException("Container provenance metadata drift: expected=$expectedScalar actual=$scalar")
        }
        val expectedFiles =
            contextFiles(context)
                .map { it.relativeTo(context).invariantSeparatorsPath }
                .filterNot { it == PROVENANCE_FILE }
                .sorted()
        val actualFiles = entries.filter { it.first == "file" }.map { it.second }
        if (actualFiles != expectedFiles) {
            throw GradleException("Container provenance file list drift: expected=$expectedFiles actual=$actualFiles")
        }
    }
}

internal fun contextFiles(root: File): List<File> =
    root.walkTopDown().filter(File::isFile).toList().sortedBy { it.relativeTo(root).invariantSeparatorsPath }

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private const val APP_JAR = "app.jar"
private const val CONTAINER_FILE = "Containerfile"
private const val CONFIGS_DIR = "configs"
private const val DATA_DIR = "data"
private const val PROVENANCE_FILE = "container-provenance.txt"
private val PROHIBITED_SEGMENTS = setOf(".git", ".gradle", "src", "buildSrc", "third_party")
