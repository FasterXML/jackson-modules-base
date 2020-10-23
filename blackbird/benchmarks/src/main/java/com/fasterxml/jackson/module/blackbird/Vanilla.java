package com.fasterxml.jackson.module.blackbird;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class Vanilla extends BaseBenchmark {
    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
            .include(Vanilla.class.getSimpleName())
            .forks(1)
            .build();
        new Runner(options).run();
    }

    @Override
    protected ObjectMapper createObjectMapper() {
        return new ObjectMapper();
    }
}
