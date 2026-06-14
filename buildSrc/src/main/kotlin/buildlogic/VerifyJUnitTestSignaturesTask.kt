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
private const val JUNIT_TEST_TEMPLATE_ANNOTATION = "org.junit.jupiter.api.TestTemplate"
private const val JUNIT_TESTABLE_ANNOTATION = "org.junit.platform.commons.annotation.Testable"

internal object JUnitTestSignatureSupport {
    fun invalidMethods(classes: Sequence<Class<*>>): List<String> =
        classes
            .flatMap { type ->
                type.declaredMethods
                    .asSequence()
                    .filter(::requiresVoidReturn)
                    .filter { method -> method.returnType != Void.TYPE }
                    .map { method ->
                        "${type.name}#${method.name} returns ${method.returnType.typeName}"
                    }
            }.sorted()
            .toList()

    fun entryPoints(classes: Sequence<Class<*>>): List<String> =
        classes
            .flatMap { type ->
                type.declaredMethods
                    .asSequence()
                    .filter(::isJUnitEntryPoint)
                    .map { method ->
                        val parameters = method.parameterTypes.joinToString(",") { parameter -> parameter.typeName }
                        "${type.name}#${method.name}($parameters)"
                    }
            }.sorted()
            .toList()

    private fun requiresVoidReturn(method: java.lang.reflect.Method): Boolean =
        method.hasAnnotationRoot(setOf(JUNIT_TEST_ANNOTATION, JUNIT_TEST_TEMPLATE_ANNOTATION))

    private fun isJUnitEntryPoint(method: java.lang.reflect.Method): Boolean =
        method.hasAnnotationRoot(setOf(JUNIT_TESTABLE_ANNOTATION))

    private fun java.lang.reflect.Method.hasAnnotationRoot(targets: Set<String>): Boolean =
        declaredAnnotations.any { annotation ->
            annotation.annotationClass.java.hasAnnotationRoot(targets, mutableSetOf())
        }

    private fun Class<out Annotation>.hasAnnotationRoot(
        targets: Set<String>,
        visited: MutableSet<String>,
    ): Boolean {
        if (name in targets) {
            return true
        }
        if (!visited.add(name)) {
            return false
        }
        return declaredAnnotations.any { annotation ->
            annotation.annotationClass.java.hasAnnotationRoot(targets, visited)
        }
    }
}

internal object JUnitCompiledTestSupport {
    fun <T> inspect(
        testClassesDirs: Collection<File>,
        testRuntimeClasspath: Collection<File>,
        inspector: (Sequence<Class<*>>) -> T,
    ): T {
        val classDirectories = testClassesDirs.filter(File::isDirectory)
        val urls = (classDirectories + testRuntimeClasspath).map(File::toURI).map { it.toURL() }.toTypedArray()
        return URLClassLoader(urls, javaClass.classLoader).use { loader ->
            inspector(loadTestClasses(classDirectories, loader))
        }
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

@DisableCachingByDefault(because = "Verification task has no outputs")
abstract class VerifyJUnitTestSignaturesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testClassesDirs: ConfigurableFileCollection

    @get:Classpath
    abstract val testRuntimeClasspath: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val invalidMethods =
            JUnitCompiledTestSupport.inspect(testClassesDirs.files, testRuntimeClasspath.files) { classes ->
                JUnitTestSignatureSupport.invalidMethods(classes)
            }

        if (invalidMethods.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("JUnit test and test-template methods must return void/Unit:")
                    invalidMethods.forEach { appendLine("- $it") }
                }.trimEnd(),
            )
        }
        logger.lifecycle("Verified JUnit test signatures: all test and test-template methods return void/Unit")
    }
}
