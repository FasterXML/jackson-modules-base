package tools.jackson.module.blackbird;

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

import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;

/**
 * Builder-based bean: one {@link Invoice} with eight scalar properties.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class BuilderBenchmark
{
    @Param({"stock", "blackbird"})
    public String mode;

    private ObjectReader reader;
    private ObjectWriter writer;
    private byte[] json;

    @Setup
    public void setup() {
        var mapper = Mappers.create(mode);
        reader = mapper.readerFor(Invoice.class);
        writer = mapper.writerFor(Invoice.class);
        json = writer.writeValueAsBytes(Invoice.SAMPLE);
    }

    @Benchmark
    public Invoice builderDeser() {
        return reader.readValue(json);
    }

    @Benchmark
    public byte[] builderSer() {
        return writer.writeValueAsBytes(Invoice.SAMPLE);
    }
}
