# Blackbird Benchmarks

Blackbird needs to show compelling performance to be a credible alternative to Afterburner.
I've run some simple benchmarks built with JMH.

```
JMH version: 1.25
VM version: JDK 14.0.2, OpenJDK 64-Bit Server VM, 14.0.2+12
Linux 5.8.4-200.fc32.x86_64
Intel(R) Core(TM) i5-5250U CPU @ 1.60GHz
```

### Deserialize Bean Array

Deserialize an array of 1000 like beans with 5 properties

```
Benchmark                           Mode  Cnt    Score    Error   Units
Vanilla.beanArrayDeser             thrpt   50    1.184 ±  0.005  ops/ms
Afterburner.beanArrayDeser         thrpt   50    1.299 ±  0.009  ops/ms
Blackbird.beanArrayDeser           thrpt   50    1.301 ±  0.005  ops/ms

VanillaGraal.beanArrayDeser        thrpt   50    1.117 ±  0.009  ops/ms
AfterburnerGraal.beanArrayDeser    thrpt   50    1.314 ±  0.015  ops/ms
BlackbirdGraal.beanArrayDeser      thrpt   50    1.333 ±  0.014  ops/ms
```

### Serialize Bean Array

Serialize an array of 1000 like beans with 5 properties

```
Vanilla.beanArraySer               thrpt   50    2.041 ±  0.014  ops/ms
Afterburner.beanArraySer           thrpt   50    2.193 ±  0.010  ops/ms
Blackbird.beanArraySer             thrpt   50    2.115 ±  0.009  ops/ms

VanillaGraal.beanArraySer          thrpt   50    2.091 ±  0.013  ops/ms
AfterburnerGraal.beanArraySer      thrpt   50    2.293 ±  0.018  ops/ms
BlackbirdGraal.beanArraySer        thrpt   50    2.256 ±  0.015  ops/ms
```

### Constructor Array Deserialize

Deserialize the same 5 property bean with a `@JsonConstructor` rather than setters

```
Vanilla.constructorArrayDeser           thrpt   50    1.114 ±  0.003  ops/ms
Afterburner.constructorArrayDeser       thrpt   50    1.124 ±  0.004  ops/ms
Blackbird.constructorArrayDeser         thrpt   50    1.122 ±  0.004  ops/ms

VanillaGraal.constructorArrayDeser      thrpt   50    1.063 ±  0.007  ops/ms
AfterburnerGraal.constructorArrayDeser  thrpt   50    1.087 ±  0.004  ops/ms
BlackbirdGraal.constructorArrayDeser    thrpt   50    1.092 ±  0.003  ops/ms
```

### 8-int Bean Deserialize

Deserialize a list of 1000 beans, each with 8 integer properties

```
Vanilla.classicBeanItemDeser          thrpt   50    1.070 ±  0.008  ops/ms
Afterburner.classicBeanItemDeser      thrpt   50    1.398 ±  0.024  ops/ms
Blackbird.classicBeanItemDeser        thrpt   50    1.250 ±  0.017  ops/ms

VanillaGraal.classicBeanItemDeser     thrpt   50    1.082 ±  0.006  ops/ms
AfterburnerGraal.classicBeanItemDeser thrpt   50    1.515 ±  0.014  ops/ms
BlackbirdGraal.classicBeanItemDeser   thrpt   50    1.480 ±  0.020  ops/ms
```

### 8-int Bean Serialize

Serialize a list of 1000 beans, each with 8 integer properties

```
Vanilla.classicBeanItemSer          thrpt   50    1.949 ±  0.020  ops/ms
Afterburner.classicBeanItemSer      thrpt   50    2.325 ±  0.024  ops/ms
Blackbird.classicBeanItemSer        thrpt   50    2.140 ±  0.017  ops/ms

VanillaGraal.classicBeanItemSer     thrpt   50    2.017 ±  0.019  ops/ms
AfterburnerGraal.classicBeanItemSer thrpt   50    2.485 ±  0.043  ops/ms
BlackbirdGraal.classicBeanItemSer   thrpt   50    2.202 ±  0.053  ops/ms
```

### Photo Metadata Deserialize

Deserialize a slightly more complicated bean with photo metadata

```
Vanilla.classicMediaItemDeser           thrpt   50  324.917 ±  3.942  ops/ms
Afterburner.classicMediaItemDeser       thrpt   50  403.335 ±  1.337  ops/ms
Blackbird.classicMediaItemDeser         thrpt   50  387.160 ±  1.507  ops/ms

VanillaGraal.classicMediaItemDeser      thrpt   50  332.992 ±  4.667  ops/ms
AfterburnerGraal.classicMediaItemDeser  thrpt   50  429.227 ±  2.092  ops/ms
BlackbirdGraal.classicMediaItemDeser    thrpt   50  430.031 ±  1.192  ops/ms
```

### Photo Metadata Serialize

Serialize a slightly more complicated bean with photo metadata

```
Vanilla.classicMediaItemSer             thrpt   50  564.082 ±  6.896  ops/ms
Afterburner.classicMediaItemSer         thrpt   50  610.446 ±  1.996  ops/ms
Blackbird.classicMediaItemSer           thrpt   50  575.804 ±  4.887  ops/ms

VanillaGraal.classicMediaItemSer        thrpt   50  611.400 ±  3.187  ops/ms
AfterburnerGraal.classicMediaItemSer    thrpt   50  676.551 ±  5.980  ops/ms
BlackbirdGraal.classicMediaItemSer      thrpt   50  659.393 ±  2.591  ops/ms
```

### Startup time

_(lower is better)_

```
StartupTimeHotspot.vanilla          ss   10  256.619 ± 14.163   ms/op
StartupTimeHotpost.afterburner      ss   10  296.559 ± 27.770   ms/op
StartupTimeHotspot.blackbird        ss   10  292.001 ± 26.581   ms/op

StartupTimeGraal  .vanilla          ss   10  298.862 ± 37.968   ms/op
StartupTimeGraal  .afterburner      ss   10  369.617 ± 59.404   ms/op
StartupTimeGraal  .blackbird        ss   10  351.144 ± 53.208   ms/op
```

Both Afterburner and Blackbird incur a slight startup penalty.
Graal imposes another startup penalty.

These JMH benchmarks should demonstrate that on modern JVMs (11+),
Blackbird is roughly comparable to Afterburner, and comfortably faster than vanilla Jackson.
If you can afford the startup time, Graal seems to generate faster code in almost all circumstances.
Right now you have to build the executable jar yourself with Maven.
Please report any problems or performance issues so we can improve Blackbird!

TODO: pretty graphs

TODO: merge this into [jackson-benchmarks](https://github.com/FasterXML/jackson-benchmarks) as appropriate
