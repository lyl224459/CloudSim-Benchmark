package datacenter

import config.CloudletGenConfig
import datacenter.generator.CloudletGeneratorFactory
import org.cloudsimplus.cloudlets.Cloudlet
import java.util.*

/**
 * 云任务生成器（统一接口）
 * 支持多种生成策略
 */
class CloudletGenerator(
    private val random: Random = Random(0),
    private val generatorType: config.CloudletGeneratorType = CloudletGenConfig.GENERATOR_TYPE
) {
    private val strategy = CloudletGeneratorFactory.createGenerator(generatorType, random)
    
    /**
     * 创建云任务列表
     * 
     * @param userId 用户ID
     * @param count 任务数量，默认为 DatacenterConfig.DEFAULT_CLOUDLET_N
     * @return 云任务列表
     */
    fun createCloudlets(userId: Int, count: Int = config.DatacenterConfig.DEFAULT_CLOUDLET_N): List<Cloudlet> {
        return strategy.createCloudlets(userId, count, random)
    }
}

