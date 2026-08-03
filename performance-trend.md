# Performance Trend Report

- Generated: `2026-08-03T05:36:10.565542091Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.3`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.626 | ms/op | 87601.020 | -8.987% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.118 | ms/op | 186448.192 | -5.655% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.306 | ms/op | 106000.498 | 2.448% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.168 | ms/op | 91224.275 | -0.502% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.001 | ms/op | 136.002 | -14.771% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.374 | ms/op | 852992.610 | -8.475% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 7.505 | ms/op | 14301532.000 | -5.998% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.123 | ms/op | 346608.201 | -6.576% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 17.408 | ms/op | 2348539.200 | -10.857% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 422.134 | ms/op | 48115288.000 | -9.198% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.288 | ms/op | 761190.915 | -5.385% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.067 | ms/op | 1525084.750 | -4.975% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 183.196 | ms/op | 28007836.000 | -3.984% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.163 | ms/op | 551043.487 | -1.587% |
