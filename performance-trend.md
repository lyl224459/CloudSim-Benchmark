# Performance Trend Report

- Generated: `2026-08-17T02:56:22.348999516Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.4`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.689 | ms/op | 92881.121 | 9.112% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.125 | ms/op | 186472.204 | 5.992% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.303 | ms/op | 106000.494 | -2.819% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.170 | ms/op | 91224.277 | -3.293% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.002 | ms/op | 136.003 | 15.837% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.373 | ms/op | 852992.607 | -4.243% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 7.615 | ms/op | 14295580.364 | 6.170% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.126 | ms/op | 373432.206 | 1.066% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 19.179 | ms/op | 2348541.143 | 10.406% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 467.035 | ms/op | 48115288.000 | 10.594% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 4.533 | ms/op | 761191.286 | 5.200% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 8.508 | ms/op | 1525085.600 | 3.889% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 193.693 | ms/op | 28007836.000 | 3.521% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 2.249 | ms/op | 551091.643 | 4.891% |
