package datacenter.generator

import config.CloudletGenConfig
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import java.util.*

/**
 * 对数正态分布生成器（默认）
 * 使用对数正态分布生成任务执行时间，正态分布生成文件大小
 * 对应 Cloudlet-Scheduler-mydev 的 createCloudlets 和 createCloudletsSCI
 */
class LogNormalCloudletGenerator(
    private val meanExecTime: Double = CloudletGenConfig.MEAN_EXEC_TIME,
    private val varianceExecTime: Double = CloudletGenConfig.VARIANCE_EXEC_TIME,
    private val meanFileSize: Double = CloudletGenConfig.MEAN_FILE_SIZE,
    private val varianceFileSize: Double = CloudletGenConfig.VARIANCE_FILE_SIZE,
    private val meanOutputSize: Double = CloudletGenConfig.MEAN_FILE_SIZE,  // 默认与输入文件大小相同
    private val varianceOutputSize: Double = CloudletGenConfig.VARIANCE_FILE_SIZE
) : CloudletGeneratorStrategy {
    
    override fun createCloudlets(userId: Int, count: Int, random: Random): List<Cloudlet> {
        val cloudletList = mutableListOf<Cloudlet>()
        val utilizationModel = UtilizationModelFull()
        val pesNumber = 1
        
        for (i in 0 until count) {
            // 使用对数正态分布生成执行时间（length）
            val length = Math.exp(random.nextGaussian() * varianceExecTime + Math.log(meanExecTime)).toLong()
            
            // 使用正态分布生成输入文件大小
            val fileSize = Math.max(10, (random.nextGaussian() * varianceFileSize + meanFileSize).toLong())
            
            // 使用正态分布生成输出文件大小
            val outputSize = Math.max(10, (random.nextGaussian() * varianceOutputSize + meanOutputSize).toLong())
            
            // 创建云任务
            val cloudlet = CloudletSimple(length, pesNumber)
                .setFileSize(fileSize)
                .setOutputSize(outputSize)
                .setUtilizationModelCpu(utilizationModel)
                .setUtilizationModelRam(utilizationModel)
                .setUtilizationModelBw(utilizationModel)
            
            cloudletList.add(cloudlet)
        }
        
        return cloudletList
    }
}

