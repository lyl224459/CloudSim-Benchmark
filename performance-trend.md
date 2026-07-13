# Performance Trend Report

- Generated: `2026-07-13T05:32:25.228300720Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.3`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.632 | ms/op | 92881.030 | 1.994% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.118 | ms/op | 186472.191 | -0.356% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.307 | ms/op | 106000.500 | 1.983% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.167 | ms/op | 91224.272 | -1.983% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.001 | ms/op | 136.002 | 0.063% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.386 | ms/op | 851368.630 | -2.724% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 7.158 | ms/op | 14287603.657 | -0.567% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.133 | ms/op | 346656.217 | 2.347% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 17.354 | ms/op | 2348539.200 | 0.441% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 428.205 | ms/op | 48115288.000 | 2.118% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.287 | ms/op | 761238.915 | -5.364% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.087 | ms/op | 1525085.161 | -0.149% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 187.090 | ms/op | 28007836.000 | 0.042% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.197 | ms/op | 551043.548 | 1.672% |
