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

@BenchmarkMode(Mode.Throughput)
@Measurement(time = 30, iterations = 10)
@Warmup(time = 10, iterations = 10)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
public abstract class BaseBenchmark {
    @State(Scope.Thread)
    public static class NewBeanState {
        Random random = new Random(1337);
        ObjectMapper mapper;
        SomeBean[] beans;
        byte[] beansBytes;
    }
    @State(Scope.Thread)
    public static class MediaItemState {
        byte[] mediaItemJson;
        ObjectWriter mediaItemWriter;
        ObjectReader mediaItemReader;
    }

    @State(Scope.Thread)
    public static class ClassicBeanState {
        ClassicBean classicBean;
        byte[] classicBeanJson;
        ObjectWriter classicBeanWriter;
        ObjectReader classicBeanReader;
    }

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
            .include(BaseBenchmark.class.getSimpleName())
            .build();
        new Runner(options).run();
    }

    protected abstract ObjectMapper createObjectMapper();

    @Setup
    public void setup(NewBeanState nbs, MediaItemState mis, ClassicBeanState cbs) throws Exception {
        nbs.mapper = createObjectMapper();
        nbs.beans = IntStream.range(0, 1000)
                .mapToObj(i -> SomeBean.random(nbs.random))
                .toArray(SomeBean[]::new);

        nbs.beansBytes = nbs.mapper.writeValueAsBytes(nbs.beans);
        mis.mediaItemReader = nbs.mapper.readerFor(MediaItem.class);
        mis.mediaItemWriter = nbs.mapper.writerFor(MediaItem.class);
        mis.mediaItemJson = mis.mediaItemWriter.writeValueAsBytes(MediaItem.SAMPLE);

        cbs.classicBeanReader = nbs.mapper.readerFor(ClassicBean.class);
        cbs.classicBeanWriter = nbs.mapper.writerFor(ClassicBean.class);
        cbs.classicBean = new ClassicBean();
        cbs.classicBean.setUp();
        cbs.classicBeanJson = cbs.classicBeanWriter.writeValueAsBytes(cbs.classicBean);
    }

    @Benchmark
    public byte[] beanArraySer(NewBeanState nbs) throws Exception {
        return nbs.mapper.writeValueAsBytes(nbs.beans);
    }

    @Benchmark
    public SomeBean[] beanArrayDeser(NewBeanState nbs) throws Exception {
        return nbs.mapper.readValue(nbs.beansBytes, SomeBean[].class);
    }

    @Benchmark
    public BeanWithPropertyConstructor[] constructorArrayDeser(NewBeanState nbs) throws Exception {
        return nbs.mapper.readValue(nbs.beansBytes, BeanWithPropertyConstructor[].class);
    }

    @Benchmark
    public void classicMediaItemSer(MediaItemState mis, Blackhole bh) throws Exception {
        mis.mediaItemWriter.writeValue(new NopOutputStream(bh), MediaItem.SAMPLE);
    }

    @Benchmark
    public MediaItem classicMediaItemDeser(MediaItemState mis) throws Exception {
        return mis.mediaItemReader.readValue(mis.mediaItemJson);
    }


    @Benchmark
    public void classicBeanItemSer(ClassicBeanState cbs, Blackhole bh) throws Exception {
        cbs.classicBeanWriter.writeValue(new NopOutputStream(bh), cbs.classicBean);
    }

    @Benchmark
    public MediaItem classicBeanItemDeser(ClassicBeanState cbs) throws Exception {
        return cbs.classicBeanReader.readValue(cbs.classicBeanJson);
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
