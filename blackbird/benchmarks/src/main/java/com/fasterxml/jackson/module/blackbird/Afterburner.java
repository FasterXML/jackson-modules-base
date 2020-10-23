package com.fasterxml.jackson.module.blackbird;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class Afterburner extends BaseBenchmark {
    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
            .include(Afterburner.class.getSimpleName())
            .forks(1)
            .build();
        new Runner(options).run();
    }

    @Override
    protected ObjectMapper createObjectMapper() {
        return new ObjectMapper().registerModule(new AfterburnerModule());
    }
}
