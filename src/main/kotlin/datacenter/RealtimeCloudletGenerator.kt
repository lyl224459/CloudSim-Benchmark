package datacenter

import config.CloudletGenConfig
import datacenter.generator.CloudletGeneratorFactory
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import java.util.*

/**
 * 实时云任务生成器
 * 生成带有到达时间的任务，模拟实时任务调度场景
 */
class RealtimeCloudletGenerator(
    private val random: Random = Random(config.DatacenterConfig.DEFAULT_RANDOM_SEED),
    private val arrivalRate: Double = 10.0,  // 平均每秒到达的任务数（泊松分布）
    private val generatorType: config.CloudletGeneratorType = CloudletGenConfig.GENERATOR_TYPE
) {
    private val strategy = CloudletGeneratorFactory.createGenerator(generatorType, random)
    
    /**
     * 创建实时云任务列表（带到达时间）
     * 
     * @param userId 用户ID
     * @param count 任务数量
     * @param simulationDuration 仿真持续时间（秒）
     * @return 云任务列表，已设置到达时间
     */
    fun createRealtimeCloudlets(
        userId: Int, 
        count: Int = config.DatacenterConfig.DEFAULT_CLOUDLET_N,
        simulationDuration: Double = 1000.0
    ): List<Cloudlet> {
        // 使用指数分布生成任务到达间隔（泊松过程的到达间隔）
        var currentTime = 0.0
        val lambda = arrivalRate  // 到达率
        
        val cloudlets = mutableListOf<Cloudlet>()
        
        // 先生成所有任务（不带到达时间）
        val baseCloudlets = strategy.createCloudlets(userId, count, random)
        
        // 为每个任务设置到达时间
        for (cloudlet in baseCloudlets) {
            // 生成到达间隔（指数分布）
            val interArrivalTime = -Math.log(1 - random.nextDouble()) / lambda
            
            // 确保任务在仿真时间内到达
            currentTime += interArrivalTime
            if (currentTime > simulationDuration) {
                break
            }
            
            // 设置任务到达时间（提交延迟）
            cloudlet.setSubmissionDelay(currentTime)
            cloudlets.add(cloudlet)
        }
        
        return cloudlets
    }
}

