package datacenter.benchmark;

import datacenter.ObjectiveBenchmarkCase;
import datacenter.PerformanceBenchmarkWorkloads;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CloudSimPerformanceBenchmarks {
    @Benchmark
    public void objectiveFunctionCalculate(ObjectiveState state, Blackhole blackhole) {
        blackhole.consume(state.objectiveCase.calculate());
    }

    @Benchmark
    public void batchMetaheuristicSchedule(BatchState state) {
        PerformanceBenchmarkWorkloads.runBatchAlgorithm(
            state.algorithm,
            state.cloudletCount,
            state.population,
            state.maxIter,
            state.seed
        );
    }

    @Benchmark
    public void realtimeSchedule(RealtimeState state) {
        PerformanceBenchmarkWorkloads.runRealtimeAlgorithm(
            state.algorithm,
            state.cloudletCount,
            state.population,
            state.maxIter,
            state.seed
        );
    }

    @State(Scope.Thread)
    public static class ObjectiveState {
        @Param({"1000"})
        public int cloudletCount;

        private ObjectiveBenchmarkCase objectiveCase;

        @Setup(Level.Trial)
        public void setup() {
            objectiveCase = PerformanceBenchmarkWorkloads.objectiveCase(cloudletCount);
        }
    }

    @State(Scope.Thread)
    public static class BatchState {
        @Param({"PSO", "WOA", "GWO", "HHO"})
        public String algorithm;

        @Param({"100"})
        public int cloudletCount;

        public int population = 10;
        public int maxIter = 10;
        public long seed = 0L;
    }

    @State(Scope.Thread)
    public static class RealtimeState {
        @Param({"MIN_LOAD", "PSO_REALTIME", "WOA_REALTIME"})
        public String algorithm;

        @Param({"50", "100", "500"})
        public int cloudletCount;

        public int population = 10;
        public int maxIter = 10;
        public long seed = 0L;
    }
}
