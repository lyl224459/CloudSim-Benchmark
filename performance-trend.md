# Performance Trend Report

- Generated: `2026-07-27T05:49:24.274759508Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.3`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.688 | ms/op | 92881.121 | 9.868% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.125 | ms/op | 186448.203 | 4.900% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.299 | ms/op | 106000.486 | -2.252% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.169 | ms/op | 91224.276 | -1.636% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.002 | ms/op | 136.003 | 15.475% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.409 | ms/op | 946568.667 | 8.958% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 7.984 | ms/op | 14295580.750 | 8.936% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.132 | ms/op | 346680.214 | 4.063% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 19.528 | ms/op | 2348543.385 | 11.926% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 464.895 | ms/op | 48115288.000 | 10.597% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.532 | ms/op | 761191.286 | 4.998% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.490 | ms/op | 1525085.600 | 4.577% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 190.798 | ms/op | 28007836.000 | 3.058% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.198 | ms/op | 551043.579 | 1.804% |
