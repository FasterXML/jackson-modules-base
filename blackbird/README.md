# Jackson Blackbird

Blackbird accelerates Jackson databind with generated per-bean codecs. For
each bean type it defines one hidden class with the JDK ClassFile API
(JEP 484) and installs it as that bean's deserializer. The generated codec
matches property names with the parser's `PropertyNameMatcher`, dispatches on
the match index with a `tableswitch`, and reads each property with direct,
monomorphic code. The JIT sees one inlinable region per bean type instead of
the stock dispatch chain.

This is the second Blackbird engine. The first (Jackson 2.x, and 3.x before
this version) built accessor lambdas with `LambdaMetafactory` inside the stock
deserializer loop. The current engine replaces the loop itself. Blackbird
2.x continues to serve Jackson 2.x users.

## Requirements

- Jackson 3.x.
- JDK 25 or later (the module is compiled with `--release 25`).

## Status

[![Maven Central](https://img.shields.io/maven-central/v/tools.jackson.module/jackson-module-blackbird.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/tools.jackson.module/jackson-module-blackbird)

The module passes the full Blackbird test suite, and generated codecs are
verified byte-identical to stock databind on the benchmark corpus.

Generated codecs unload with the mapper: the hidden classes are anchored only
by the codec instances in the mapper's caches. The old engine's lambdas stayed
alive with the target ClassLoader, so applications that created many mappers
ran out of metaspace. That limitation is gone.

## Usage

### Maven dependency

Blackbird is available on Maven Central:

```xml
<dependency>
  <groupId>tools.jackson.module</groupId>
  <artifactId>jackson-module-blackbird</artifactId>
</dependency>
```

### Registering the module

Register the module with the mapper:

```java
ObjectMapper mapper = JsonMapper.builder()
    .addModule(new BlackbirdModule())
    .build();
```

then do data binding as usual:

```java
Value val = mapper.readValue(jsonSource, Value.class);
mapper.writeValue(new File("result.json"), val);
```

On the module path, grant Blackbird access to your classes by supplying a
`MethodHandles.Lookup` from your own module. Override `findLookup()` or
`findLookupSupplier()` on `BlackbirdModule` to customize which lookup is used
for which class. Record deserialization needs a lookup with access to the
record's canonical constructor.

## What is optimized?

Deserialization (JSON to POJOs):

- Setter-based POJOs: direct construction and direct setter calls.
- Records: typed locals in canonical-constructor order and a single
  constructor call, replacing the generic creator buffering. This is the
  largest measured win.
- Builder-based beans (for example Immutables types): fluent setter calls
  inlined, build method called through a constant MethodHandle.
- Scalar properties (`String`, `int`, `long`, `boolean`, enums) read with
  direct parser calls. Nested beans call the child codec directly. Lists of
  strings and of beans use inline loops.

Serialization (POJOs to JSON): straight-line writers with pre-encoded name
constants, direct getter calls, and inline list loops. Property writes go
through per-type generated helpers sized to compile as standalone units,
which avoids a C2 code-quality penalty for inlined copies of hot jackson-core
methods inside looping writer bodies.

Properties and beans outside this coverage keep stock behavior by
construction. A property with a custom deserializer, non-default coercion or
null handling, polymorphic typing, or injection routes through the stock
property implementation inside the generated codec. Beans with features the
generator does not cover keep the stock `BeanDeserializer` outright.

## What is not?

- Streaming parser and generator access.
- The tree model.

## Performance

Measured with paired JMH runs on JDK 25/26, aarch64 and x86_64, against the
previous Blackbird engine: setter POJOs +7-9%, record graphs +48-49%, builder
beans +19-20%. Against vanilla databind, record graphs measure up to +124%.
Record and builder acceleration is new; the previous engine left both on the
stock path.
