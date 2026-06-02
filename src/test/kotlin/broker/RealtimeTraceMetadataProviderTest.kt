package broker

import config.RealtimeSchedulingConfig
import datacenter.DatacenterCreator
import datacenter.DatacenterType
import datacenter.RealtimeCloudletSpec
import datacenter.RealtimeTraceMetadata
import datacenter.RealtimeTraceMetadataProvider
import org.assertj.core.api.Assertions.assertThat
import org.cloudsimplus.cloudlets.Cloudlet
import org.cloudsimplus.cloudlets.CloudletSimple
import org.cloudsimplus.core.CloudSimPlus
import org.cloudsimplus.utilizationmodels.UtilizationModelFull
import org.cloudsimplus.vms.Vm
import org.cloudsimplus.vms.VmSimple
import org.junit.jupiter.api.Test
import scheduler.RealtimeMinLoadScheduler
import scheduler.RealtimeResourceModel

class RealtimeTraceMetadataProviderTest {
    @Test
    fun `resource model reads explicit metadata provider`() {
        val cloudlet = createCloudlet()
        val provider =
            RealtimeTraceMetadataProvider.fromSpecs(
                listOf(
                    RealtimeCloudletSpec(
                        cloudlet = cloudlet,
                        traceMetadata = RealtimeTraceMetadata(requestedRam = 512.0, requestedBw = 128.0, requestedIo = 64.0),
                    ),
                ),
            )
        val model =
            RealtimeResourceModel(
                enabled = true,
                networkLatency = 0.0,
                imagePullDelay = 0.0,
                ioWeight = 1.0,
                ramWeight = 1.0,
                bwWeight = 1.0,
                traceMetadataProvider = provider,
            )

        assertThat(model.ramDemand(cloudlet)).isEqualTo(512.0)
        assertThat(model.bwDemand(cloudlet)).isEqualTo(128.0)
        assertThat(model.ioDemand(cloudlet)).isEqualTo(64.0)
    }

    @Test
    fun `broker submit specs realtime stores metadata without registry`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val cloudlet = createCloudlet(id = 42)
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    multiTenantEnabled = true,
                    tenantCount = 3,
                    priorityLevels = 4,
                    resourceModelEnabled = true,
                ),
            )

        broker.submitVmList(listOf(vm))
        broker.submitCloudletSpecsRealtime(
            listOf(
                RealtimeCloudletSpec(
                    cloudlet = cloudlet,
                    traceMetadata =
                        RealtimeTraceMetadata(
                            tenantKey = "tenant-a",
                            tenantId = 5,
                            priority = 3,
                            requestedRam = 256.0,
                        ),
                ),
            ),
        )

        val metadata = broker.getTaskMetadata(cloudlet)
        assertThat(metadata?.tenantId?.value).isEqualTo(2)
        assertThat(metadata?.tenantKey).isEqualTo("tenant-a")
        assertThat(metadata?.priority).isEqualTo(3)
        assertThat(metadata?.requestedRam).isEqualTo(256.0)
    }

    @Test
    fun `legacy cloudlet list submission keeps trace metadata empty`() {
        val simulation = CloudSimPlus()
        DatacenterCreator.createDatacenter(simulation, "test-dc", DatacenterType.LOW)
        val vm = createVm()
        val cloudlet = createCloudlet(id = 43)
        val broker =
            RealtimeBroker(
                simulation,
                RealtimeMinLoadScheduler(listOf(vm)),
                listOf(vm),
                RealtimeSchedulingConfig(
                    multiTenantEnabled = true,
                    tenantCount = 3,
                    priorityLevels = 4,
                    resourceModelEnabled = true,
                ),
            )

        broker.submitVmList(listOf(vm))
        broker.submitCloudletListRealtime(listOf(cloudlet))

        val metadata = broker.getTaskMetadata(cloudlet)
        assertThat(metadata?.tenantKey).isNull()
        assertThat(metadata?.requestedRam).isNull()
        assertThat(metadata?.requestedBw).isNull()
        assertThat(metadata?.requestedIo).isNull()
    }

    private fun createVm(): Vm =
        VmSimple(1000.0, 1)
            .setRam(1024)
            .setBw(1000)
            .setSize(10000)

    private fun createCloudlet(id: Int = 0): Cloudlet {
        val utilizationModel = UtilizationModelFull()
        return CloudletSimple(1000, 1).apply {
            setId(id.toLong())
            setFileSize(100)
            setOutputSize(100)
            setSubmissionDelay(0.1)
            setUtilizationModelCpu(utilizationModel)
            setUtilizationModelRam(utilizationModel)
            setUtilizationModelBw(utilizationModel)
        }
    }
}
