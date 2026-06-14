package buildlogic

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

abstract class CloudSimPlusBuildService : BuildService<BuildServiceParameters.None>, AutoCloseable {
    override fun close() = Unit
}
