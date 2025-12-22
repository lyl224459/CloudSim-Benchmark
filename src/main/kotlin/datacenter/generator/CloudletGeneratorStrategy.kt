package datacenter.generator

import org.cloudsimplus.cloudlets.Cloudlet
import java.util.*

/**
 * 云任务生成器策略接口
 */
interface CloudletGeneratorStrategy {
    /**
     * 创建云任务列表
     * 
     * @param userId 用户ID
     * @param count 任务数量
     * @param random 随机数生成器
     * @return 云任务列表
     */
    fun createCloudlets(userId: Int, count: Int, random: Random): List<Cloudlet>
}

