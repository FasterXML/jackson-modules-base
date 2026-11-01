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
 * Wide setter beans ({@link Wide40}, {@link Wide200}) with every property set.
 * The 200-property bean is large enough that generated methods can pass the
 * HotSpot limits for JIT compilation of huge methods.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class WideBeanBenchmark
{
    @Param({"stock", "blackbird"})
    public String mode;

    @Param({"40", "200"})
    public int width;

    private ObjectReader reader;
    private ObjectWriter writer;
    private byte[] json;
    private Object value;

    @Setup
    public void setup() {
        Class<?> type = switch (width) {
            case 40 -> Wide40.class;
            case 200 -> Wide200.class;
            default -> throw new IllegalArgumentException("Unknown width: " + width);
        };
        var mapper = Mappers.create(mode);
        reader = mapper.readerFor(type);
        writer = mapper.writerFor(type);
        json = sampleJson(width).getBytes();
        value = reader.readValue(json);
    }

    private static String sampleJson(int width) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < width; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("\"p").append(i).append("\":");
            switch (i % 4) {
                case 0 -> sb.append("\"value-").append(i).append('"');
                case 1 -> sb.append(i * 31);
                case 2 -> sb.append(1_000_000_007L * i);
                default -> sb.append(i % 3 == 0);
            }
        }
        return sb.append('}').toString();
    }

    @Benchmark
    public Object wideDeser() {
        return reader.readValue(json);
    }

    @Benchmark
    public byte[] wideSer() {
        return writer.writeValueAsBytes(value);
    }
}
