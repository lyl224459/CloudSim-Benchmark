# Performance Trend Report

- Generated: `2026-09-28T08:05:12.361153605Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.4.1`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.621 | ms/op | 92881.012 | -1.052% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.129 | ms/op | 186448.209 | 9.152% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.305 | ms/op | 106000.497 | -2.293% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.170 | ms/op | 91224.276 | -0.409% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.002 | ms/op | 136.002 | 0.714% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.394 | ms/op | 852968.643 | -0.767% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 7.681 | ms/op | 14287604.364 | 16.028% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.126 | ms/op | 346632.206 | -1.411% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 17.297 | ms/op | 2348539.200 | -1.769% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 424.127 | ms/op | 48115288.000 | 0.997% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.280 | ms/op | 761190.915 | -0.796% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.067 | ms/op | 1525084.750 | -1.506% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 185.738 | ms/op | 28007836.000 | 1.847% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.159 | ms/op | 551043.517 | 0.279% |
