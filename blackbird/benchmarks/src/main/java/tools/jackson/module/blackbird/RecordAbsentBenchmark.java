package tools.jackson.module.blackbird;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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

import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.node.ObjectNode;

/**
 * Records with one absent component: none, or one of a String, an Optional,
 * a List or an OptionalInt.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class RecordAbsentBenchmark
{
    public record Payload(String foo, double bar, long qux, int corge) {}

    public record Envelope(String id, long ts, String err, Optional<String> note,
            List<String> tags, OptionalInt code, Payload data) {}

    static final Envelope SAMPLE = new Envelope("abcdefghij.0123456789", 1700000000123L,
            "none", Optional.of("note"), List.of("a", "b"), OptionalInt.of(42),
            new Payload("abcdefg", 12345.5, 123456789L, 42));

    @Param({"stock", "blackbird"})
    public String mode;

    @Param({"none", "err", "note", "tags", "code"})
    public String absent;

    private ObjectReader reader;
    private byte[] json;

    @Setup
    public void setup() {
        ObjectMapper mapper = Mappers.create(mode);
        if (mode.equals("blackbird")) {
            // A record off the codegen path would measure stock here.
            DeserializationContext ctxt = mapper._deserializationContext();
            String deser = ctxt.findRootValueDeserializer(ctxt.constructType(Envelope.class))
                    .getClass().getName();
            if (!deser.contains(".BBReader_")) {
                throw new IllegalStateException("Envelope has no generated reader: " + deser);
            }
        }
        reader = mapper.readerFor(Envelope.class);
        ObjectNode tree = mapper.valueToTree(SAMPLE);
        tree.remove(absent);
        json = mapper.writeValueAsBytes(tree);
    }

    @Benchmark
    public Envelope read() {
        return reader.readValue(json);
    }
}
