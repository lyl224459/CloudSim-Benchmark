package config

/**
 * 批处理调度算法类型
 */
enum class BatchAlgorithmType {
    RANDOM,  // 随机调度
    PSO,     // 粒子群优化
    WOA,     // 鲸鱼优化
    GWO,     // 灰狼优化
    HHO      // 哈里斯鹰优化
}

/**
 * 实时调度算法类型
 */
enum class RealtimeAlgorithmType {
    MIN_LOAD,      // 最小负载调度
    RANDOM,        // 随机调度
    PSO_REALTIME,  // PSO实时调度
    WOA_REALTIME   // WOA实时调度
}

