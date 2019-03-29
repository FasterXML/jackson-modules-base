package com.fasterxml.jackson.module.blackbird;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.commons.lang3.RandomStringUtils;
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
    public MediaItem classicBeanItemDeser() throws Exception {
        return classicBeanReader.readValue(classicBeanJson);
    }

    public static class SomeBean {
        public int getPropA() {
            return propA;
        }

        public void setPropA(int propA) {
            this.propA = propA;
        }

        public String getPropB() {
            return propB;
        }

        public void setPropB(String propB) {
            this.propB = propB;
        }

        public long getPropC() {
            return propC;
        }

        public void setPropC(long propC) {
            this.propC = propC;
        }

        public SomeBean getPropD() {
            return propD;
        }

        public void setPropD(SomeBean propD) {
            this.propD = propD;
        }

        public SomeEnum getPropE() {
            return propE;
        }

        public void setPropE(SomeEnum propE) {
            this.propE = propE;
        }

        private int propA;
        private String propB;
        private long propC;
        private SomeBean propD;
        private SomeEnum propE;

        public enum SomeEnum {
            EA, EB, EC, ED
        }

        static SomeBean random(Random random) {
            final SomeBean result = new SomeBean();
            result.setPropA(random.nextInt());
            result.setPropB(RandomStringUtils.randomAscii(random.nextInt(32)));
            result.setPropC(random.nextLong());
            if (random.nextInt(5) == 0) {
                result.setPropD(random(random));
            }
            result.setPropE(SomeEnum.values()[random.nextInt(SomeEnum.values().length)]);
            return result;
        }
    }

    public static class BeanWithPropertyConstructor extends SomeBean {
        @SuppressWarnings("unused")
        private BeanWithPropertyConstructor() {
            throw new UnsupportedOperationException();
        }
        @JsonCreator
        public BeanWithPropertyConstructor(
                @JsonProperty("propA") int propA,
                @JsonProperty("propB") String propB,
                @JsonProperty("propC") long propC,
                @JsonProperty("propD") SomeBean propD,
                @JsonProperty("propE") SomeEnum propE)
        {
            setPropA(propA);
            setPropB(propB);
            setPropC(propC);
            setPropD(propD);
            setPropE(propE);
        }
    }

    public final static class ClassicBean
    {

        public int a, b, c123, d;
        public int e, foobar, g, habitus;

        public ClassicBean setUp() {
            a = 1;
            b = 999;
            c123 = -1000;
            d = 13;
            e = 6;
            foobar = -33;
            g = 0;
            habitus = 123456789;
            return this;
        }

        public void setA(int v) { a = v; }
        public void setB(int v) { b = v; }
        public void setC(int v) { c123 = v; }
        public void setD(int v) { d = v; }

        public void setE(int v) { e = v; }
        public void setF(int v) { foobar = v; }
        public void setG(int v) { g = v; }
        public void setH(int v) { habitus = v; }

        public int getA() { return a; }
        public int getB() { return b; }
        public int getC() { return c123; }
        public int getD() { return d; }
        public int getE() { return e; }
        public int getF() { return foobar; }
        public int getG() { return g; }
        public int getH() { return habitus; }
    }
}
