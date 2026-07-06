# Performance Trend Report

- Generated: `2026-07-06T06:24:24.399096607Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.3`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.619 | ms/op | 87601.007 | 16.363% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.118 | ms/op | 186448.192 | 22.545% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.301 | ms/op | 106000.487 | 30.826% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.170 | ms/op | 91224.278 | 27.808% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.001 | ms/op | 136.002 | 13.181% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.397 | ms/op | 852968.644 | 29.670% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 7.199 | ms/op | 14301531.657 | 31.670% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.130 | ms/op | 346704.212 | 25.600% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 17.278 | ms/op | 2348539.200 | 16.273% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 419.323 | ms/op | 48115288.000 | 15.947% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.530 | ms/op | 761239.286 | 28.527% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.099 | ms/op | 1525084.750 | 21.861% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 187.012 | ms/op | 28007836.000 | 25.524% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.161 | ms/op | 551043.517 | 25.639% |
