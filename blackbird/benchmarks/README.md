# Blackbird benchmarks

JMH benchmarks that compare stock databind, the classic Blackbird engine
(LambdaMetafactory, release 3.2.2) and the generated-codec engine of this
build.

## Benchmarks

Each benchmark class has a `mode` parameter: `stock` (no module),
`blackbird` or `afterburner`. The default run measures `stock` and
`blackbird`.

- `PojoBenchmark`: setter POJOs. Arrays of 1000 small beans, the same beans
  through a property-based `@JsonCreator`, the media item bean, and arrays
  of 1000 beans with 8 `int` properties.
- `RecordBenchmark`: a record with a nested record and a list of 3 records.
- `BuilderBenchmark`: a bean with 8 properties and a fluent builder
  (`@JsonPOJOBuilder`).
- `WideBeanBenchmark`: setter beans with 40 and 200 properties.
- `ColdStartBenchmark`: the first read and write of 500 distinct types
  (250 records, 250 setter beans) in a fresh JVM, with 20 forks. The score
  is the time for all 500 types.

## How to run

Blackbird needs JDK 25 or later. The benchmark module is not part of the
reactor, so install the modules first:

```
./mvnw -pl blackbird,afterburner -am install -DskipTests
cd blackbird/benchmarks
../../mvnw package
../../mvnw package -Pclassic-blackbird
```

The first `package` writes `target/benchmarks.jar` (this build). The
`classic-blackbird` profile writes `target/classic/benchmarks-classic.jar`,
which has Blackbird 3.2.2 and the same databind. Run the two jars with the
same JMH options:

```
java -jar target/benchmarks.jar -f 3 -wi 5 -w 1s -i 5 -r 1s \
    'Pojo|Record|Builder|WideBean' -rf json -rff new.json
java -jar target/classic/benchmarks-classic.jar -f 3 -wi 5 -w 1s -i 5 -r 1s \
    'Pojo|Record|Builder|WideBean' -rf json -rff classic.json
java -jar target/benchmarks.jar ColdStart -rf json -rff cold-new.json
java -jar target/classic/benchmarks-classic.jar ColdStart \
    -rf json -rff cold-classic.json
```

Each run measures stock databind too. Divide each Blackbird score by the
stock score of the same run. A ratio across two runs includes any change
in the load of the host between the runs.

Do not give `-wi` or `-i` to `ColdStartBenchmark`. A warmup iteration
makes the measured iteration warm.

To see which beans get a generated codec, add
`-jvmArgsAppend -Dblackbird.debug.codegen=true`.

## Results

JMH 1.37, JDK 25.0.4, Linux on x86_64 (AMD Ryzen 7 5800X3D, 8 cores and 16
threads), with the options above and `-Xms512m -Xmx512m` in each fork.
Other processes used 1 to 2 CPUs during the runs, and a run during which
they used more than 3 CPUs was repeated. Scores are ops/ms with the 99.9%
confidence interval. Classic is Blackbird 3.2.2, and new is this build.
Each Blackbird column has the stock score of its own run.

| Benchmark | Stock (classic run) | Classic | Stock (new run) | New |
|---|---:|---:|---:|---:|
| BuilderBenchmark.builderDeser | 2074 ± 19 | 2093 ± 25 | 2166 ± 18 | 2719 ± 40 |
| BuilderBenchmark.builderSer | 2955 ± 79 | 3576 ± 139 | 3077 ± 51 | 4429 ± 146 |
| PojoBenchmark.beanArrayDeser | 2.65 ± 0.07 | 2.80 ± 0.06 | 2.58 ± 0.08 | 3.09 ± 0.06 |
| PojoBenchmark.beanArraySer | 4.73 ± 0.07 | 4.76 ± 0.12 | 4.53 ± 0.14 | 5.54 ± 0.14 |
| PojoBenchmark.classicBeanDeser | 2.45 ± 0.11 | 2.61 ± 0.08 | 2.48 ± 0.07 | 3.31 ± 0.03 |
| PojoBenchmark.classicBeanSer | 4.09 ± 0.13 | 4.65 ± 0.08 | 4.18 ± 0.08 | 6.65 ± 0.17 |
| PojoBenchmark.constructorArrayDeser | 1.87 ± 0.04 | 2.35 ± 0.05 | 1.87 ± 0.03 | 1.90 ± 0.04 |
| PojoBenchmark.mediaItemDeser | 738 ± 6 | 718 ± 11 | 736 ± 18 | 894 ± 16 |
| PojoBenchmark.mediaItemSer | 1201 ± 29 | 1217 ± 24 | 1176 ± 26 | 1402 ± 51 |
| RecordBenchmark.recordDeser | 598 ± 9 | 837 ± 34 | 600 ± 11 | 1391 ± 15 |
| RecordBenchmark.recordSer | 1859 ± 44 | 2016 ± 42 | 1839 ± 35 | 2252 ± 90 |
| WideBeanBenchmark.wideDeser (40) | 527 ± 33 | 558 ± 10 | 545 ± 12 | 738 ± 16 |
| WideBeanBenchmark.wideDeser (200) | 87.0 ± 2.1 | 90.2 ± 2.4 | 88.4 ± 1.9 | 87.1 ± 1.5 |
| WideBeanBenchmark.wideSer (40) | 813 ± 14 | 1013 ± 26 | 825 ± 21 | 1418 ± 30 |
| WideBeanBenchmark.wideSer (200) | 134 ± 4 | 152 ± 3 | 138 ± 8 | 286 ± 2 |

`ColdStartBenchmark.firstUse`, ms for 500 types (lower is better): classic
run stock 416 ± 11 and classic 911 ± 8, new run stock 415 ± 6 and new
925 ± 10.

The new engine does not generate a reader for `BeanWithPropertyConstructor`
(property-based creator), so `constructorArrayDeser` measures the stock
deserializer in the new column. It also does not generate a reader for
`Wide200`: the generated method would be larger than 7000 bytes, which
HotSpot does not compile.
