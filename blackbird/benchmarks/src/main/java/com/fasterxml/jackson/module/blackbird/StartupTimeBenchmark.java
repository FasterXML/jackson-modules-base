package com.fasterxml.jackson.module.blackbird;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.SingleShotTime)
@Measurement(iterations = 1)
@Warmup(iterations = 0)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(10)
public class StartupTimeBenchmark {
    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
            .include(StartupTimeBenchmark.class.getSimpleName())
            .build();
        new Runner(options).run();
    }

    @Benchmark
    public byte[] vanilla() throws Exception {
        return singleShot(new ObjectMapper());
    }

    @Benchmark
    public byte[] blackbird() throws Exception {
        return singleShot(new ObjectMapper().registerModule(new BlackbirdModule()));
    }

    @Benchmark
    public byte[] afterburner() throws Exception {
        return singleShot(new ObjectMapper().registerModule(new AfterburnerModule()));
    }

    private static byte[] singleShot(ObjectMapper mapper) throws JsonProcessingException {
        final Random random = new Random();
        return mapper.writeValueAsBytes(List.of(
                SomeBean.random(random),
                new ClassicBean().setUp(),
                new BeanWithPropertyConstructor(42, "foo", 8675309, SomeBean.random(random), SomeEnum.EB)));
    }
}
