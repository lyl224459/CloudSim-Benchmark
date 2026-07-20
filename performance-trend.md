# Performance Trend Report

- Generated: `2026-07-20T05:38:27.487108587Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.3`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.627 | ms/op | 92881.020 | -0.800% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.119 | ms/op | 186448.194 | 1.140% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.306 | ms/op | 106000.498 | -0.364% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.172 | ms/op | 91224.281 | 3.110% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.002 | ms/op | 136.002 | 2.226% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.376 | ms/op | 852968.612 | -2.765% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 7.329 | ms/op | 16355579.657 | 2.397% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.127 | ms/op | 346680.207 | -4.485% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 17.448 | ms/op | 2348539.200 | 0.539% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 420.350 | ms/op | 48115288.000 | -1.835% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.316 | ms/op | 761191.034 | 0.685% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.118 | ms/op | 1618685.161 | 0.384% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 185.135 | ms/op | 28007836.000 | -1.045% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.159 | ms/op | 551091.517 | -1.756% |
