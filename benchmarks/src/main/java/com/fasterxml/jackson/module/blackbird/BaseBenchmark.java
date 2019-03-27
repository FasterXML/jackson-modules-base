package com.fasterxml.jackson.module.blackbird;

import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@Measurement(time = 5)
@Warmup(time = 2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
public abstract class BaseBenchmark {
    ObjectMapper mapper;
    SomeBean bean;
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
        bean = new SomeBean();

        bean.setPropA(42);
        bean.setPropB("This is a moderately long string");
        bean.setPropC(Long.MIN_VALUE);
        bean.setPropD("This is an even better string for testing moderately long data sizes");
        bean.setPropE(SomeBean.SomeEnum.EC);

        bytes = mapper.writeValueAsBytes(bean);
    }

    @Benchmark
    public byte[] beanSer() throws Exception {
        return mapper.writeValueAsBytes(bean);
    }

    @Benchmark
    public SomeBean beanDeser() throws Exception {
        return mapper.readValue(bytes, SomeBean.class);
    }

    @Benchmark
    public SomeBean constructorDeser() throws Exception {
        return mapper.readValue(bytes, BeanWithPropertyConstructor.class);
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

        public String getPropD() {
            return propD;
        }

        public void setPropD(String propD) {
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
        private String propD;
        private SomeEnum propE;

        public enum SomeEnum {
            EA, EB, EC, ED
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
                @JsonProperty("propD") String propD,
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
