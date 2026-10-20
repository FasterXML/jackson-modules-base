package tools.jackson.module.blackbird;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import tools.jackson.databind.ObjectMapper;

/**
 * First use of 500 distinct types ({@link ColdTypes}) in a fresh JVM: one
 * read and one write of each type, which includes introspection and, with
 * Blackbird, code generation. Divide the score by 500 for the cost per type.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(20)
public class ColdStartBenchmark
{
    private static final byte[] JSON = ("{\"a\":\"s\",\"b\":7,\"c\":9,\"d\":true,"
            + "\"e\":\"t\",\"f\":3,\"g\":1.5,\"h\":\"u\"}").getBytes();

    @Param({"stock", "blackbird"})
    public String mode;

    private ObjectMapper mapper;
    private Class<?>[] types;

    @Setup
    public void setup() {
        mapper = Mappers.create(mode);
        types = ColdTypes.class.getClasses();
        Arrays.sort(types, Comparator.comparing(Class::getName));
    }

    @Benchmark
    public void firstUse(Blackhole bh) {
        for (Class<?> type : types) {
            Object value = mapper.readValue(JSON, type);
            bh.consume(mapper.writeValueAsBytes(value));
        }
    }
}
