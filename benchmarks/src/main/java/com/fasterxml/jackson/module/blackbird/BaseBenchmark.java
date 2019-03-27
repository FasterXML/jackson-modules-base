package com.fasterxml.jackson.module.blackbird;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Measurement(time = 20)
@Warmup(time = 10)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
public abstract class BaseBenchmark {
    static Random random = new Random(1337);
    ObjectMapper mapper;
    SomeBean[] beans;
    byte[] bytes;

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
            .include(BaseBenchmark.class.getSimpleName())
            .forks(1)
            .build();
        new Runner(options).run();
    }

    protected abstract ObjectMapper createObjectMapper();

    @Setup
    public void setup() throws Exception {
        mapper = createObjectMapper();
        beans = IntStream.range(0, 1000)
                .mapToObj(i -> SomeBean.random())
                .toArray(SomeBean[]::new);

        bytes = mapper.writeValueAsBytes(beans);
    }

    @Benchmark
    public byte[] beanSer() throws Exception {
        return mapper.writeValueAsBytes(beans);
    }

    @Benchmark
    public SomeBean[] beanDeser() throws Exception {
        return mapper.readValue(bytes, SomeBean[].class);
    }

    @Benchmark
    public BeanWithPropertyConstructor[] constructorDeser() throws Exception {
        return mapper.readValue(bytes, BeanWithPropertyConstructor[].class);
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

        static SomeBean random() {
            final SomeBean result = new SomeBean();
            result.setPropA(random.nextInt());
            result.setPropB(RandomStringUtils.randomAscii(random.nextInt(32)));
            result.setPropC(random.nextLong());
            if (random.nextInt(5) == 0) {
                result.setPropD(random());
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
}
