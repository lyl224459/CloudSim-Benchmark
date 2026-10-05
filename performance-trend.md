# Performance Trend Report

- Generated: `2026-10-05T07:59:34.163374424Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.4.1`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.628 | ms/op | 92881.023 | 1.116% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.118 | ms/op | 186472.193 | -7.938% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.301 | ms/op | 106000.490 | -1.412% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.171 | ms/op | 91224.276 | 0.609% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.001 | ms/op | 136.002 | -2.617% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.379 | ms/op | 852920.615 | -3.878% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 6.914 | ms/op | 14295603.027 | -9.986% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.124 | ms/op | 346680.203 | -1.240% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 17.534 | ms/op | 2348539.200 | 1.371% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 421.865 | ms/op | 48115288.000 | -0.533% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.297 | ms/op | 761190.915 | 0.397% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.212 | ms/op | 1525085.161 | 1.793% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 183.450 | ms/op | 28007836.000 | -1.232% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.161 | ms/op | 551043.517 | 0.093% |
