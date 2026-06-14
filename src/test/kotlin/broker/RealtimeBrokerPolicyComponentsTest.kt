package broker

import config.RealtimeSchedulingConfig
import datacenter.RealtimeCloudletSpec
import datacenter.RealtimeTraceMetadata
import datacenter.RealtimeTraceMetadataProvider
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test

class RealtimeBrokerPolicyComponentsTest {
    @Test
    fun `metadata factory freezes trace resource and tenant fields`() {
        val cloudlet = createCloudlet(id = 42, length = 2_000)
        val scheduling =
            RealtimeSchedulingConfig(
                tenantCount = 3,
                priorityLevels = 5,
                regionCount = 2,
                deadlineFactor = 2.0,
            )
        val provider =
            RealtimeTraceMetadataProvider.fromSpecs(
                listOf(
                    RealtimeCloudletSpec(
                        cloudlet = cloudlet,
                        traceMetadata =
                            RealtimeTraceMetadata(
                                tenantKey = "tenant-a",
                                tenantId = 5,
                                priority = 4,
                                dataRegion = 7,
                                requestedCpu = 2.0,
                                requestedRam = 512.0,
                                requestedBw = 128.0,
                                requestedIo = 64.0,
                            ),
                    ),
                ),
            )
        val factory =
            RealtimeTaskMetadataFactory(
                scheduling,
                provider,
                RealtimeTenantController(scheduling),
            ) { _, _, _ -> 0.0 }

        val record =
            factory.create(
                RealtimeTaskMetadataRequest(
                    cloudlet = cloudlet,
                    arrivalTime = 10.0,
                    attempt = 2,
                    fastestVmMips = 1_000.0,
                ),
            )

        assertThat(record.tenantId.value).isEqualTo(2)
        assertThat(record.tenantKey).isEqualTo("tenant-a")
        assertThat(record.priority).isEqualTo(4)
        assertThat(record.deadline).isEqualTo(14.0)
        assertThat(record.dataRegion?.value).isEqualTo(1)
        assertThat(record.requestedCpu).isEqualTo(2.0)
        assertThat(record.requestedRam).isEqualTo(512.0)
        assertThat(record.requestedBw).isEqualTo(128.0)
        assertThat(record.requestedIo).isEqualTo(64.0)
        assertThat(record.attempt).isEqualTo(2)
    }

    @Test
    fun `vm reservation policy keeps legacy partial and full reservation choices`() {
        val activeOnMedium = createCloudlet(id = 1, length = 1_000).also { it.setVm(createVm(id = 1, mips = 1_000.0)) }
        val vmList =
            listOf(
                createVm(id = 0, mips = 500.0),
                createVm(id = 1, mips = 1_000.0),
                createVm(id = 2, mips = 1_000.0),
                createVm(id = 3, mips = 2_000.0),
            )

        val partial =
            RealtimeVmReservationPolicy(RealtimeSchedulingConfig(resourceReservation = "partial"))
                .select(
                    selectedVmId = 3,
                    cloudlet = createCloudlet(id = 2),
                    activeCloudlets = emptyList(),
                    vmList = vmList,
                )
        val full =
            RealtimeVmReservationPolicy(RealtimeSchedulingConfig(resourceReservation = "full"))
                .select(
                    selectedVmId = 0,
                    cloudlet = createCloudlet(id = 3, length = 30_000),
                    activeCloudlets = listOf(activeOnMedium),
                    vmList = vmList,
                )

        assertThat(partial).isEqualTo(0)
        assertThat(full).isEqualTo(2)
    }

    private fun createCloudlet(
        id: Int,
        length: Long = 1_000,
    ): Cloudlet {
        val utilizationModel = UtilizationModelFull()
        return CloudletSimple(length, 1).apply {
            setId(id.toLong())
            setUtilizationModelCpu(utilizationModel)
            setUtilizationModelRam(utilizationModel)
            setUtilizationModelBw(utilizationModel)
        }
    }

    private fun createVm(
        id: Int,
        mips: Double,
    ): Vm =
        VmSimple(mips, 1)
            .also { it.setId(id.toLong()) }
            .setRam(1024)
            .setBw(1000)
            .setSize(10_000)
            .setCloudletScheduler(CloudletSchedulerSpaceShared())
}
