# Performance Trend Report

- Generated: `2026-08-10T03:47:47.953434220Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.3`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.631 | ms/op | 92881.028 | 0.736% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.118 | ms/op | 186448.192 | 0.147% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.312 | ms/op | 106000.504 | 1.925% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.176 | ms/op | 91224.283 | 4.621% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.002 | ms/op | 136.002 | 1.700% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.389 | ms/op | 852968.635 | 3.933% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 7.172 | ms/op | 14287579.657 | -4.437% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.125 | ms/op | 346704.204 | 1.273% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 17.372 | ms/op | 2348539.200 | -0.210% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 422.299 | ms/op | 48115288.000 | 0.039% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.309 | ms/op | 761190.915 | 0.491% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.190 | ms/op | 1525085.161 | 1.521% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 187.106 | ms/op | 28007836.000 | 2.134% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.144 | ms/op | 551043.487 | -0.858% |
