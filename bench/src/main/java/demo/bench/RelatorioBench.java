package demo.bench;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Slide 7: concatenação em laço x StringBuilder, medido do jeito certo.
 * Rode com: java -jar target/benchmarks.jar -prof gc
 * O -prof gc mostra a alocação por operação (gc.alloc.rate.norm).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
@State(Scope.Benchmark)
public class RelatorioBench {

    @Param({"100", "400", "1000"})
    int linhas;

    @Benchmark
    public void concatenacao(Blackhole bh) {
        String s = "";
        for (int i = 0; i < linhas; i++) {
            s += "PED-" + i + ";Categoria 07;1234,56\n";
        }
        bh.consume(s);
    }

    @Benchmark
    public void builder(Blackhole bh) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < linhas; i++) {
            sb.append("PED-").append(i).append(";Categoria 07;1234,56\n");
        }
        bh.consume(sb.toString());
    }
}
