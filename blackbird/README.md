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

- Jackson 3.x, with `jackson-core` and `jackson-databind` of the same version.
  If the release-25 classes fail to initialize, for example because a
  `jackson-core` or `jackson-databind` of another version does not have a
  class that they need, Blackbird behaves as on Java 24. The warning or the
  exception names the error.
- Java 25 or later at run time. The code generators use the ClassFile API
  (final in Java 24) and compile at release 25, the current LTS release. The
  jar is a multi-release jar: the generators are under
  `META-INF/versions/25`, and all other classes are release 17. On Java 17
  to 24, module discovery (`MapperBuilder.findAndAddModules()`) skips
  Blackbird with one logged warning, and the mapper works without it. An
  explicit `addModule(new BlackbirdModule())` throws
  `IllegalStateException`.
- If you repackage Blackbird into an uber jar (for example with the Maven
  Shade plugin), keep `Multi-Release: true` in the manifest of that jar.
  Without it, the JVM does not see the release-25 classes, and Blackbird
  behaves as on Java 24.
- Building the module needs JDK 25 or later. On an older JDK the reactor
  leaves the Blackbird modules out, and a release build (`-Prelease`) fails.

## Status

[![Maven Central](https://img.shields.io/maven-central/v/tools.jackson.module/jackson-module-blackbird.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/tools.jackson.module/jackson-module-blackbird)

The module passes the full Blackbird test suite.

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

No access setup is required beyond what stock databind already needs. On the
module path, that is `opens your.package to tools.jackson.databind` for
non-public classes and members; Blackbird adds no requirement of its own.
Generated codecs reach members through `MethodHandle` constants unreflected
after databind's own access checks, so everything stock databind can read or
write - private members, non-public classes, records, foreign classloaders -
accelerates. A member databind cannot open falls back to the stock path,
which fails (or succeeds) exactly as it would without Blackbird.

The `BlackbirdModule` constructors and overrides that supply a
`MethodHandles.Lookup` (`findLookup()`, `findLookupSupplier()`) remain for
compatibility, but Blackbird no longer uses the lookup: no acceleration
needs it. The module exports only `tools.jackson.module.blackbird`.
Blackbird defines the generated codecs in its own module.

## What is optimized?

Blackbird generates a codec for a bean only where the generated code does
what stock databind does. The codec is a copy of the stock deserializer or
serializer whose main read or write method is generated. Inputs and features
that the generated code does not handle go to the stock code. Other modifiers
and databind's own checks therefore see the stock `BeanDeserializer`,
`BuilderBasedDeserializer` or `BeanSerializer` types.

Deserialization (JSON to POJOs):

- POJOs that stock builds with a no-arg constructor: construction and
  stores through constant `MethodHandle`s, which the JIT inlines like direct
  calls. Non-public setters and constructors accelerate the same way public
  ones do. Non-final fields (any visibility) store through constant field
  handles. Final fields keep stock behavior.
- Records with their canonical constructor: typed locals in
  canonical-constructor order and a direct constructor call. When the
  document leaves out a component, the stock creator completes the
  arguments (absent values such as `Optional.empty()`, required and
  `FAIL_ON_MISSING_CREATOR_PROPERTIES` checks).
- Builder-based beans (for example Immutables types): fluent setter calls
  through constant handles, and the build method called through a constant
  handle.
- `String`, `int`, `long` and `boolean` values are read with direct parser
  calls, and nested beans that have a generated codec are read by that codec.
  All other values, and any value with an unexpected token (such as `null` or
  a string for a number), go through the stock property.

Serialization (POJOs to JSON): straight-line writers with pre-encoded name
constants and direct getter or field reads. Property writes go through one
generated class of write helpers that all writers share. The helpers are
sized to compile as standalone units, which avoids a C2 code-quality penalty
for inlined copies of hot jackson-core methods inside looping writer bodies,
and one shared copy keeps the compiled code small.

A property keeps the stock property implementation inside the generated
codec when it has polymorphic type handling (`@JsonTypeInfo` or default
typing), a custom deserializer or serializer, a property wrapper (managed and
back references, or wrappers from a `ValueDeserializerModifier`), a
non-default null serializer, an applied view, or value suppression. A bean
keeps the stock deserializer or serializer when it has Object Ids, a
delegating or property-based creator (other than the canonical constructor
of a record), an external type id, unwrapped or merging properties, or, for
records, an any-setter or injectable values. A bean whose own shape is
`@JsonFormat(shape = JsonFormat.Shape.ARRAY)` (by annotation or config
override) keeps the stock deserializer and serializer: stock reads and
writes it with its own as-array code. A bean also keeps the stock code
when one of its generated methods would be larger than 7000 bytes of
bytecode: HotSpot does not compile methods over 8000 bytes.

## What is not?

- Streaming parser and generator access.
- The tree model.

## Debugging

With `-Dblackbird.debug.codegen=true`, the module prints each generation
decision (engaged, or which gate demoted the bean) to standard error. With
`-Dblackbird.debug.dumpDir=<dir>`, the module writes each generated class's
bytes to that directory as `<bean-class-name>-reader.class` or
`-writer.class`, for inspection with javap or a decompiler.

When code generation fails for a bean, the bean keeps the stock path, and
the module logs the failure at `DEBUG` (`FINE` in java.util.logging) through
the `System.Logger` named `tools.jackson.module.blackbird`. One case is not
reported anywhere: when generation runs out of stack, deep in the resolution
of a deeply nested type, the bean keeps the stock path silently (logging on
that stack could fail again). The codegen trace then shows the bean's
`reader resolve` or `writer resolve` line with no outcome after it.

## Performance

The table gives throughput relative to stock databind. Each ratio is from
one JMH run that measured both engines: the classic column from a run of
the classic jar, and the new column from a run of this version. "Classic"
is Blackbird 3.2.2, the last release of the LambdaMetafactory engine, with
the same databind.

| Benchmark                                   | Classic | This version |
|---------------------------------------------|--------:|-------------:|
| Record read (nested record, list of 3)      |   1.40x |        2.32x |
| Record write                                |   1.08x |        1.22x |
| Builder bean read (8 properties)            |   1.01x |        1.26x |
| Builder bean write                          |   1.21x |        1.44x |
| Setter POJO read (media item)               |   0.97x |        1.22x |
| Setter POJO write (media item)              |   1.01x |        1.19x |
| 1000 beans, 8 `int` properties, read        |   1.07x |        1.34x |
| 1000 beans, 8 `int` properties, write       |   1.14x |        1.59x |
| 1000 beans, 5 properties, read              |   1.06x |        1.20x |
| 1000 beans, 5 properties, write             |   1.01x |        1.22x |
| Same beans through a `@JsonCreator`, read   |   1.26x |        1.02x |
| 40-property bean, read                      |   1.06x |        1.36x |
| 40-property bean, write                     |   1.25x |        1.72x |
| 200-property bean, read                     |   1.04x |        0.99x |
| 200-property bean, write                    |   1.14x |        2.07x |

Two rows are slower than classic Blackbird. In both, this version keeps the
stock deserializer:

- A POJO with a property-based `@JsonCreator` stays on the stock
  deserializer. Classic Blackbird accelerates that creator.
- The generated reader of the 200-property bean would be larger than 7000
  bytes of bytecode, so the bean keeps the stock deserializer. HotSpot does
  not compile methods over 8000 bytes (`DontCompileHugeMethods`), and an
  interpreted reader was 3.7 times slower than stock.

Cold start costs more than stock. The first read and write of 500 distinct
types (250 records and 250 setter beans) in a fresh JVM take 0.83 ms per
type with stock databind, 1.85 ms with this version and 1.82 ms with
classic Blackbird. Thus each bean type costs approximately 1 ms more on
first use, for introspection, code generation and class definition.

Environment: JMH 1.37, JDK 25.0.4, Linux on x86_64 (AMD Ryzen 7 5800X3D,
8 cores and 16 threads), 3 forks of 5 iterations of 1 s after 5 warmup
iterations, and 20 forks for cold start. Other processes used 1 to 2 CPUs
during the runs, and a run during which they used more than 3 CPUs was
repeated. For the read rows, whose code did not change, an earlier run on
the same host with less than 0.4 CPU of other load gave ratios within
0.07x of these. To run the benchmarks, see
[benchmarks/README.md](benchmarks/README.md).
