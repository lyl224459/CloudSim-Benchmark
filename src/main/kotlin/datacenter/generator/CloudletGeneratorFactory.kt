package datacenter.generator

import config.CloudletGeneratorType
import config.GoogleTraceConfig

/**
 * 云任务生成器工厂
 */
internal object CloudletGeneratorFactory {
    /**
     * 创建生成器实例
     */
    fun createGenerator(
        type: CloudletGeneratorType,
        googleTraceConfig: GoogleTraceConfig? = null,
    ): CloudletGeneratorStrategy =
        when (type) {
            CloudletGeneratorType.LOG_NORMAL -> LogNormalCloudletGenerator()
            CloudletGeneratorType.UNIFORM -> UniformCloudletGenerator()
            CloudletGeneratorType.LOG_NORMAL_SCI ->
                LogNormalCloudletGenerator(
                    meanOutputSize = 100.0,
                    varianceOutputSize = 20.0,
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
