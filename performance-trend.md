# Performance Trend Report

- Generated: `2026-08-31T07:59:04.530756440Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.4.1`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.621 | ms/op | 92881.010 | -9.910% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.118 | ms/op | 186472.192 | -7.021% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.312 | ms/op | 106000.508 | 2.032% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.169 | ms/op | 91224.276 | -0.086% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.001 | ms/op | 136.002 | -14.552% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.374 | ms/op | 852992.608 | -6.275% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 7.024 | ms/op | 14287603.333 | 3.493% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.126 | ms/op | 346632.206 | -2.824% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 17.530 | ms/op | 2348539.200 | -9.231% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 417.532 | ms/op | 48115288.000 | -11.554% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.340 | ms/op | 761239.034 | -4.473% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.064 | ms/op | 1525084.750 | -4.906% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 182.619 | ms/op | 28007836.000 | -5.411% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.157 | ms/op | 551091.517 | -1.854% |
