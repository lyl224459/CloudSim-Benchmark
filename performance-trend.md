# Performance Trend Report

- Generated: `2026-10-12T08:21:37.812391633Z`
- JVM: `OpenJDK 64-Bit Server VM`
- JDK: `25.0.4.1`
- JVM args: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xms1g -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dlogback.configurationFile=/home/runner/work/CloudSim-Benchmark/CloudSim-Benchmark/src/main/resources/cloudsim-benchmark-logback.xml`
- GC profiler: `gc`

| Benchmark | Mode | Score | Unit | Allocation/op | Delta vs baseline |
| :--- | :--- | ---: | :--- | ---: | ---: |
| batchMetaheuristicSchedule [algorithm=GWO, cloudletCount=100] | avgt | 0.543 | ms/op | 87552.885 | -13.550% |
| batchMetaheuristicSchedule [algorithm=HHO, cloudletCount=100] | avgt | 0.096 | ms/op | 176320.157 | -18.596% |
| batchMetaheuristicSchedule [algorithm=PSO, cloudletCount=100] | avgt | 0.229 | ms/op | 101152.374 | -23.746% |
| batchMetaheuristicSchedule [algorithm=WOA, cloudletCount=100] | avgt | 0.134 | ms/op | 85896.219 | -21.457% |
| objectiveFunctionCalculate [cloudletCount=1000] | avgt | 0.001 | ms/op | 88.002 | -11.098% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=100] | avgt | 0.311 | ms/op | 852968.507 | -17.854% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=500] | avgt | 6.184 | ms/op | 14295577.951 | -10.564% |
| realtimeSchedule [algorithm=MIN_LOAD, cloudletCount=50] | avgt | 0.101 | ms/op | 346656.164 | -19.018% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=100] | avgt | 15.196 | ms/op | 2346072.000 | -13.334% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=500] | avgt | 364.028 | ms/op | 48115288.000 | -13.710% |
| realtimeSchedule [algorithm=PSO_REALTIME, cloudletCount=50] | avgt | 3.531 | ms/op | 761093.746 | -17.836% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=100] | avgt | 6.625 | ms/op | 1525082.737 | -19.319% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=500] | avgt | 148.824 | ms/op | 28007836.000 | -18.875% |
| realtimeSchedule [algorithm=WOA_REALTIME, cloudletCount=50] | avgt | 1.728 | ms/op | 551021.589 | -20.017% |
