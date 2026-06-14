package buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Exposes buildSrc task types to Gradle TestKit fixture projects.
 */
class BuildLogicTestFixturePlugin : Plugin<Project> {
    override fun apply(target: Project) = Unit
}
