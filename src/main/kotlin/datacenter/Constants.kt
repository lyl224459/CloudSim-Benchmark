package datacenter

import config.DatacenterConfig

/**
 * 数据中心和虚拟机配置常量
 * @deprecated 请使用 config.DatacenterConfig
 */
@Deprecated("使用 config.DatacenterConfig", ReplaceWith("DatacenterConfig"))
object Constants {
    const val L_MIPS = DatacenterConfig.L_MIPS
    const val M_MIPS = DatacenterConfig.M_MIPS
    const val H_MIPS = DatacenterConfig.H_MIPS
    const val L_PRICE = DatacenterConfig.L_PRICE
    const val M_PRICE = DatacenterConfig.M_PRICE
    const val H_PRICE = DatacenterConfig.H_PRICE
    const val L_VM_N = DatacenterConfig.L_VM_N
    const val M_VM_N = DatacenterConfig.M_VM_N
    const val H_VM_N = DatacenterConfig.H_VM_N
    const val RAM = DatacenterConfig.RAM
    const val STORAGE = DatacenterConfig.STORAGE
    const val IMAGE_SIZE = DatacenterConfig.IMAGE_SIZE
    const val BW = DatacenterConfig.BW
    const val CLOUDLET_N = DatacenterConfig.DEFAULT_CLOUDLET_N
    const val DEFAULT_RANDOM_SEED = 0L
}

/**
 * 数据中心类型
 */
enum class DatacenterType {
    LOW,
    MEDIUM,
    HIGH
}

