# Performance Trend Report

- Generated: `2026-08-24T03:01:56.362228122Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.4`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.689 | ms/op | 92881.121 | 0.053% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.127 | ms/op | 186472.207 | 1.467% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.305 | ms/op | 106000.498 | 0.733% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.169 | ms/op | 91224.276 | -0.541% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.002 | ms/op | 136.003 | -1.474% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.399 | ms/op | 852968.651 | 7.177% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 6.787 | ms/op | 16363579.027 | -10.873% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.130 | ms/op | 346704.212 | 2.970% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 19.313 | ms/op | 2350943.385 | 0.694% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 472.076 | ms/op | 48115288.000 | 1.079% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.543 | ms/op | 761191.286 | 0.216% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.480 | ms/op | 1525085.600 | -0.334% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 193.066 | ms/op | 28007836.000 | -0.324% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.198 | ms/op | 551043.579 | -2.272% |
