package buildlogic

import java.io.File
import java.net.URI
import java.util.jar.JarFile

internal object CloudSimPlusMavenSupport {
    fun mavenExecutable(
        source: File,
        osName: String = System.getProperty("os.name"),
    ): String {
        val windows = osName.lowercase().contains("windows")
        val wrapperScript =
            if (windows) {
                source.resolve("mvnw.cmd")
            } else {
                source.resolve("mvnw")
            }
        return if (hasUsableMavenWrapper(source, wrapperScript)) {
            wrapperScript.absolutePath
        } else {
            if (windows) "mvn.cmd" else "mvn"
        }
    }

    fun hasUsableMavenWrapper(
        source: File,
        wrapperScript: File,
    ): Boolean {
        if (!wrapperScript.isFile) {
            return false
        }
        val wrapperJar = source.resolve(".mvn/wrapper/maven-wrapper.jar")
        if (!wrapperJar.isFile) {
            return false
        }
        return runCatching {
            JarFile(wrapperJar).use { jar ->
                jar.getEntry("org/apache/maven/wrapper/MavenWrapperMain.class") != null
            }
        }.getOrDefault(false)
    }

    fun mavenOptions(
        proxy: String,
        existingOptions: String? = System.getenv("MAVEN_OPTS")?.takeIf(String::isNotBlank),
    ): String {
        val proxyOptions =
            proxy
                .trim()
                .takeIf(String::isNotBlank)
                ?.let(::proxyOptions)
        return listOfNotNull(existingOptions, proxyOptions).joinToString(" ")
    }

    private fun proxyOptions(proxy: String): String {
        val proxyUri = URI(proxy)
        val proxyHost = proxyUri.host ?: proxy.substringAfter("://", proxy).substringBefore(":")
        val proxyPort =
            proxyUri.port
                .takeIf { it > 0 }
                ?: proxy.substringAfterLast(":", "").toIntOrNull()
                ?: DEFAULT_PROXY_PORT
        return listOf(
            "-Dhttp.proxyHost=$proxyHost",
            "-Dhttp.proxyPort=$proxyPort",
            "-Dhttps.proxyHost=$proxyHost",
            "-Dhttps.proxyPort=$proxyPort",
        ).joinToString(" ")
    }

    private const val DEFAULT_PROXY_PORT = 80
}
