package com.fasterxml.jackson.module.blackbird;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Measurement(time = 30, iterations = 10)
@Warmup(time = 10, iterations = 10)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
public abstract class BaseBenchmark {

    Random random = new Random(1337);
    ObjectMapper mapper;
    SomeBean[] beans;
    byte[] beansBytes;
    byte[] mediaItemJson;
    ObjectWriter mediaItemWriter;
    ObjectReader mediaItemReader;
    ClassicBean classicBean;
    byte[] classicBeanJson;
    ObjectWriter classicBeanWriter;
    ObjectReader classicBeanReader;

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
            .include(BaseBenchmark.class.getSimpleName())
            .build();
        new Runner(options).run();
    }

    protected abstract ObjectMapper createObjectMapper();

    @Setup
    public void setup() throws Exception {
        mapper = createObjectMapper();
        beans = IntStream.range(0, 1000)
                .mapToObj(i -> SomeBean.random(random))
                .toArray(SomeBean[]::new);

        beansBytes = mapper.writeValueAsBytes(beans);
        mediaItemReader = mapper.readerFor(MediaItem.class);
        mediaItemWriter = mapper.writerFor(MediaItem.class);
        mediaItemJson = mediaItemWriter.writeValueAsBytes(MediaItem.SAMPLE);

        classicBeanReader = mapper.readerFor(ClassicBean.class);
        classicBeanWriter = mapper.writerFor(ClassicBean.class);
        classicBean = new ClassicBean();
        classicBean.setUp();
        classicBeanJson = classicBeanWriter.writeValueAsBytes(classicBean);
    }

    @Benchmark
    public byte[] beanArraySer() throws Exception {
        return mapper.writeValueAsBytes(beans);
    }

    @Benchmark
    public SomeBean[] beanArrayDeser() throws Exception {
        return mapper.readValue(beansBytes, SomeBean[].class);
    }

    @Benchmark
    public BeanWithPropertyConstructor[] constructorArrayDeser() throws Exception {
        return mapper.readValue(beansBytes, BeanWithPropertyConstructor[].class);
    }

    @Benchmark
    public void classicMediaItemSer(Blackhole bh) throws Exception {
        mediaItemWriter.writeValue(new NopOutputStream(bh), MediaItem.SAMPLE);
    }

    @Benchmark
    public MediaItem classicMediaItemDeser() throws Exception {
        return mediaItemReader.readValue(mediaItemJson);
    }


    @Benchmark
    public void classicBeanItemSer(Blackhole bh) throws Exception {
        classicBeanWriter.writeValue(new NopOutputStream(bh), classicBean);
    }

    @Benchmark
    public ClassicBean classicBeanItemDeser() throws Exception {
        return classicBeanReader.readValue(classicBeanJson);
    }
}
