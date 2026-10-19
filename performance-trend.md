# Performance Trend Report

- Generated: `2026-10-19T07:10:26.553349656Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.4.1`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.646 | ms/op | 87553.052 | 18.944% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.084 | ms/op | 176128.137 | -12.585% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.297 | ms/op | 101152.484 | 29.626% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.116 | ms/op | 85896.190 | -13.270% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.001 | ms/op | 88.002 | -21.705% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.211 | ms/op | 852968.344 | -32.111% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 4.650 | ms/op | 14295599.556 | -24.795% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.070 | ms/op | 346656.114 | -30.762% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 14.335 | ms/op | 2345942.667 | -5.665% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 325.766 | ms/op | 48115288.000 | -10.511% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 3.341 | ms/op | 761093.440 | -5.365% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 5.673 | ms/op | 1525081.067 | -14.376% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 127.449 | ms/op | 28007836.000 | -14.363% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 1.451 | ms/op | 551018.358 | -16.034% |
