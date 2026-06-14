package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.URLClassLoader
import java.util.Locale

private const val JUNIT_TEST_ANNOTATION = "org.junit.jupiter.api.Test"

internal object JUnitTestSignatureSupport {
    fun invalidMethods(classes: Sequence<Class<*>>): List<String> =
        classes
            .flatMap { type ->
                type.declaredMethods
                    .asSequence()
                    .filter(::isJUnitTest)
                    .filter { method -> method.returnType != Void.TYPE }
                    .map { method ->
                        "${type.name}#${method.name} returns ${method.returnType.typeName}"
                    }
            }.sorted()
            .toList()

    private fun isJUnitTest(method: java.lang.reflect.Method): Boolean =
        method.declaredAnnotations.any { annotation ->
            annotation.annotationClass.java.name == JUNIT_TEST_ANNOTATION
        }
}

@DisableCachingByDefault(because = "Verification task has no outputs")
abstract class VerifyJUnitTestSignaturesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testClassesDirs: ConfigurableFileCollection

    @get:Classpath
    abstract val testRuntimeClasspath: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val classDirectories = testClassesDirs.files.filter(File::isDirectory)
        val urls = (classDirectories + testRuntimeClasspath.files).map(File::toURI).map { it.toURL() }.toTypedArray()
        val invalidMethods =
            URLClassLoader(urls, javaClass.classLoader).use { loader ->
                JUnitTestSignatureSupport.invalidMethods(loadTestClasses(classDirectories, loader))
            }

        if (invalidMethods.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("JUnit @Test methods must return void/Unit:")
                    invalidMethods.forEach { appendLine("- $it") }
                }.trimEnd(),
            )
        }
        logger.lifecycle("Verified JUnit @Test signatures: all methods return void/Unit")
    }

    private fun loadTestClasses(
        classDirectories: List<File>,
        loader: ClassLoader,
    ): Sequence<Class<*>> =
        classDirectories
            .asSequence()
            .flatMap { directory ->
                directory
                    .walkTopDown()
                    .filter { file -> file.isFile && file.extension.lowercase(Locale.ROOT) == "class" }
                    .map { file -> file.relativeTo(directory).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.') }
            }.distinct()
            .map { className ->
                try {
                    Class.forName(className, false, loader)
                } catch (cause: LinkageError) {
                    throw GradleException("Cannot inspect JUnit test class $className: ${cause.message}", cause)
                } catch (cause: ClassNotFoundException) {
                    throw GradleException("Cannot inspect JUnit test class $className: ${cause.message}", cause)
                }
            }
}
