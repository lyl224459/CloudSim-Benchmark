# Performance Trend Report

- Generated: `2026-09-07T06:51:21.409263169Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.4.1`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.617 | ms/op | 92881.005 | -0.637% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.117 | ms/op | 176344.191 | -0.651% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.306 | ms/op | 106000.498 | -1.831% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.169 | ms/op | 91224.276 | 0.025% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.001 | ms/op | 136.002 | -0.226% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.372 | ms/op | 852992.606 | -0.687% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 6.856 | ms/op | 14295579.027 | -2.385% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.124 | ms/op | 346704.203 | -1.543% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 17.426 | ms/op | 2348539.200 | -0.592% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 418.032 | ms/op | 48115288.000 | 0.120% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.296 | ms/op | 761238.915 | -1.020% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.097 | ms/op | 1525085.161 | 0.411% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 184.829 | ms/op | 28007904.000 | 1.210% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.151 | ms/op | 551043.487 | -0.292% |
