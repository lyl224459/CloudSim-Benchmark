package datacenter.generator

import config.CloudletGeneratorType
import config.GoogleTraceConfig
import java.util.*

/**
 * 云任务生成器工厂
 */
object CloudletGeneratorFactory {
    /**
     * 创建生成器实例
     */
    fun createGenerator(
        type: CloudletGeneratorType,
        random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED),
        googleTraceConfig: GoogleTraceConfig? = null
    ): CloudletGeneratorStrategy {
        return when (type) {
            CloudletGeneratorType.LOG_NORMAL -> LogNormalCloudletGenerator()
            CloudletGeneratorType.UNIFORM -> UniformCloudletGenerator()
            CloudletGeneratorType.LOG_NORMAL_SCI -> LogNormalCloudletGenerator(
                meanOutputSize = 100.0,
                varianceOutputSize = 20.0
            )
            CloudletGeneratorType.GOOGLE_TRACE -> {
                if (googleTraceConfig != null) {
                    GoogleTraceCloudletGenerator(googleTraceConfig)
                } else {
                    GoogleTraceCloudletGenerator()
                }
            }
        }
    }
}

