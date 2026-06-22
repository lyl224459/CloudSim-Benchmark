package config

/**
 * 批处理调度算法类型
 */
enum class BatchAlgorithmType {
    RANDOM, // 随机调度
    PSO, // 粒子群优化
    WOA, // 鲸鱼优化
    GWO, // 灰狼优化
    HHO, // 哈里斯鹰优化
    RL, // 强化学习调度
    IMPROVED_RL, // 改进版强化学习调度
}

/**
 * 实时调度算法类型
 */
enum class RealtimeAlgorithmType {
    MIN_LOAD, // 最小负载调度
    RANDOM, // 随机调度
    EDF_REALTIME, // 最早截止时间实时调度
    LLF_REALTIME, // 最小松弛时间实时调度
    EFT_REALTIME, // 最早完成时间实时调度
    SRPT_REALTIME, // 最短剩余处理时间实时调度
    PRIORITY_DEADLINE_REALTIME, // 优先级和截止时间混合实时调度
    PSO_REALTIME, // PSO实时调度
    WOA_REALTIME, // WOA实时调度
}
