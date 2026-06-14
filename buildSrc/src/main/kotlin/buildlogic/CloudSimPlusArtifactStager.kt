package buildlogic

import org.gradle.api.GradleException
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal object CloudSimPlusArtifactStager {
    fun stage(
        sourceDir: File,
        rawMavenRepo: File,
        artifactGroup: String,
        artifactName: String,
        artifactVersion: String,
    ): List<File> {
        val targetDir = sourceDir.resolve("target")
        val expectedJarName = "$artifactName-$artifactVersion.jar"
        val runtimeJars =
            targetDir
                .listFiles()
                .orEmpty()
                .filter { file -> file.isRuntimeJar(artifactName) }
        if (runtimeJars.size != 1 || runtimeJars.singleOrNull()?.name != expectedJarName) {
            throw GradleException(
                "Expected exactly one CloudSim Plus runtime jar '$expectedJarName' in ${targetDir.path}, " +
                    "found ${runtimeJars.map(File::getName).sorted()}",
            )
        }
        val sourcePom = sourceDir.resolve("pom.xml")
        if (!sourcePom.isFile) {
            throw GradleException("CloudSim Plus POM not found: ${sourcePom.path}")
        }
        val pomVersion = readProjectVersion(sourcePom)
        if (pomVersion != artifactVersion) {
            throw GradleException(
                "CloudSim Plus POM version mismatch: expected $artifactVersion, found ${pomVersion ?: "missing"}",
            )
        }

        rawMavenRepo.deleteRecursively()
        val artifactDir =
            rawMavenRepo.resolve(
                "${artifactGroup.replace('.', File.separatorChar)}${File.separator}$artifactName${File.separator}$artifactVersion",
            )
        artifactDir.mkdirs()
        val stagedJar = artifactDir.resolve(expectedJarName)
        val stagedPom = artifactDir.resolve("$artifactName-$artifactVersion.pom")
        runtimeJars.single().copyTo(stagedJar, overwrite = true)
        sourcePom.copyTo(stagedPom, overwrite = true)
        return listOf(stagedJar, stagedPom)
    }

    private fun File.isRuntimeJar(artifactName: String): Boolean =
        isFile &&
            extension == "jar" &&
            name.startsWith("$artifactName-") &&
            !name.endsWith("-sources.jar") &&
            !name.endsWith("-javadoc.jar")

    private fun readProjectVersion(pom: File): String? {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
        val project = factory.newDocumentBuilder().parse(pom).documentElement
        return (0 until project.childNodes.length)
            .asSequence()
            .map(project.childNodes::item)
            .firstOrNull { node -> node.localName == "version" || node.nodeName == "version" }
            ?.textContent
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }
}
