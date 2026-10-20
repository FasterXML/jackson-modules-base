package tools.jackson.module.blackbird;

import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

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
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;

/**
 * Setter POJOs: arrays of 1000 small beans, the same beans through a
 * property-based creator, and the media item bean.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class PojoBenchmark
{
    @Param({"stock", "blackbird"})
    public String mode;

    private ObjectMapper mapper;
    private SomeBean[] beans;
    private byte[] beansJson;
    private ObjectReader mediaItemReader;
    private ObjectWriter mediaItemWriter;
    private byte[] mediaItemJson;
    private ObjectReader classicBeanReader;
    private ObjectWriter classicBeanWriter;
    private ClassicBean[] classicBeans;
    private byte[] classicBeansJson;

    @Setup
    public void setup() {
        mapper = Mappers.create(mode);
        Random random = new Random(1337);
        beans = IntStream.range(0, 1000)
                .mapToObj(i -> SomeBean.random(random))
                .toArray(SomeBean[]::new);
        beansJson = mapper.writeValueAsBytes(beans);

        mediaItemReader = mapper.readerFor(MediaItem.class);
        mediaItemWriter = mapper.writerFor(MediaItem.class);
        mediaItemJson = mediaItemWriter.writeValueAsBytes(MediaItem.SAMPLE);

        classicBeanReader = mapper.readerFor(ClassicBean[].class);
        classicBeanWriter = mapper.writerFor(ClassicBean[].class);
        classicBeans = Collections.nCopies(1000, new ClassicBean().setUp())
                .toArray(new ClassicBean[0]);
        classicBeansJson = classicBeanWriter.writeValueAsBytes(classicBeans);
    }

    @Benchmark
    public byte[] beanArraySer() {
        return mapper.writeValueAsBytes(beans);
    }

    @Benchmark
    public SomeBean[] beanArrayDeser() {
        return mapper.readValue(beansJson, SomeBean[].class);
    }

    @Benchmark
    public BeanWithPropertyConstructor[] constructorArrayDeser() {
        return mapper.readValue(beansJson, BeanWithPropertyConstructor[].class);
    }

    @Benchmark
    public void mediaItemSer(Blackhole bh) {
        mediaItemWriter.writeValue(new NopOutputStream(bh), MediaItem.SAMPLE);
    }

    @Benchmark
    public MediaItem mediaItemDeser() {
        return mediaItemReader.readValue(mediaItemJson);
    }

    @Benchmark
    public void classicBeanSer(Blackhole bh) {
        classicBeanWriter.writeValue(new NopOutputStream(bh), classicBeans);
    }

    @Benchmark
    public ClassicBean[] classicBeanDeser() {
        return classicBeanReader.readValue(classicBeansJson);
    }
}
