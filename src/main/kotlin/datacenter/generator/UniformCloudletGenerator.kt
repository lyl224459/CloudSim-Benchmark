package datacenter.generator

import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import java.util.*

/**
 * 均匀分布生成器
 * 使用均匀分布生成所有参数
 * 对应 Cloudlet-Scheduler-mydev 的 createCloudlets1
 */
class UniformCloudletGenerator(
    private val minLength: Long = 10000L,
    private val maxLength: Long = 50000L,
    private val minFileSize: Long = 10L,
    private val maxFileSize: Long = 200L,
    private val minOutputSize: Long = 10L,
    private val maxOutputSize: Long = 200L
) : CloudletGeneratorStrategy {
    
    override fun createCloudlets(userId: Int, count: Int, random: Random): List<Cloudlet> {
        val cloudletList = mutableListOf<Cloudlet>()
        val utilizationModel = UtilizationModelFull()
        val pesNumber = 1
        
        val lengthRange = maxLength - minLength
        val fileSizeRange = maxFileSize - minFileSize
        val outputSizeRange = maxOutputSize - minOutputSize
        
        for (i in 0 until count) {
            // 使用均匀分布生成执行时间（length）
            val length = (random.nextDouble() * lengthRange).toLong() + minLength
            
            // 使用均匀分布生成输入文件大小
            val fileSize = (random.nextDouble() * fileSizeRange).toLong() + minFileSize
            
            // 使用均匀分布生成输出文件大小
            val outputSize = (random.nextDouble() * outputSizeRange).toLong() + minOutputSize
            
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

