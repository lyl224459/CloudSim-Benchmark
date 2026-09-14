# Performance Trend Report

- Generated: `2026-09-14T07:29:50.643826734Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.4.1`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.622 | ms/op | 92881.012 | 0.811% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.117 | ms/op | 186472.191 | 0.269% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.310 | ms/op | 106000.506 | 1.484% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.167 | ms/op | 91224.273 | -1.295% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.001 | ms/op | 136.002 | 0.257% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.381 | ms/op | 852968.621 | 2.479% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 6.641 | ms/op | 14287578.737 | -3.139% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.128 | ms/op | 346632.209 | 3.043% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 17.357 | ms/op | 2348539.200 | -0.399% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 417.767 | ms/op | 48115288.000 | -0.063% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.518 | ms/op | 761191.286 | 5.166% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.116 | ms/op | 1525085.161 | 0.233% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 188.641 | ms/op | 28007836.000 | 2.062% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.166 | ms/op | 551043.517 | 0.696% |
