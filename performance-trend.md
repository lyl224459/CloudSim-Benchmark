# Performance Trend Report

- Generated: `2026-09-21T07:29:31.567850828Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.4.1`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.628 | ms/op | 92881.023 | 0.957% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.118 | ms/op | 186472.192 | 0.266% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.312 | ms/op | 106000.509 | 0.559% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.170 | ms/op | 91224.278 | 1.873% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.002 | ms/op | 136.002 | 1.780% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.397 | ms/op | 852968.648 | 4.224% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 6.620 | ms/op | 14287578.737 | -0.320% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.128 | ms/op | 346656.208 | -0.238% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 17.608 | ms/op | 2348539.200 | 1.449% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 419.942 | ms/op | 48115288.000 | 0.521% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.314 | ms/op | 761190.915 | -4.495% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.191 | ms/op | 1525085.161 | 0.918% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 182.370 | ms/op | 28007836.000 | -3.324% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.153 | ms/op | 551043.487 | -0.610% |
