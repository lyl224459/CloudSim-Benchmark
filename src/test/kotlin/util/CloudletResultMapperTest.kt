package util

import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test

class CloudletResultMapperTest {

    @Test
    fun `maps original cloudlets to finished vm ids by id regardless of order`() {
        val vm0 = VmSimple(1000.0, 1).also { it.setId(10) }
        val vm1 = VmSimple(1500.0, 1).also { it.setId(11) }
        val original = listOf(
            CloudletSimple(1000, 1).also { it.setId(100) },
            CloudletSimple(1100, 1).also { it.setId(101) },
            CloudletSimple(1200, 1).also { it.setId(102) }
        )
        val finished = listOf(
            original[2].setVm(vm1),
            original[0].setVm(vm0)
        )

        val mapping = mapCloudletsToVmIds(original, finished)

        assertThat(mapping.toList()).containsExactly(vm0.id.toInt(), 0, vm1.id.toInt())
    }
}
