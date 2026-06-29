# Performance Trend Report

- Generated: `2026-06-29T06:33:45.542267081Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.3`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.532 | ms/op | 87552.866 | n/a |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.096 | ms/op | 176152.157 | n/a |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.230 | ms/op | 101152.375 | n/a |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.133 | ms/op | 85896.217 | n/a |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.001 | ms/op | 88.002 | n/a |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.306 | ms/op | 852968.499 | n/a |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 5.467 | ms/op | 14287576.870 | n/a |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.103 | ms/op | 373480.168 | n/a |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 14.860 | ms/op | 2346040.000 | n/a |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 361.651 | ms/op | 48115288.000 | n/a |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 3.525 | ms/op | 761093.746 | n/a |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 6.646 | ms/op | 1525082.737 | n/a |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 148.986 | ms/op | 28007836.000 | n/a |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 1.720 | ms/op | 551021.589 | n/a |
