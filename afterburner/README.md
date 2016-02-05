Module that will add dynamic bytecode generation for standard Jackson POJO serializers and deserializers, eliminating majority of remaining data binding overhead.

Plugs in using standard Module interface (requiring Jackson 2.0.0 or above).

For Javadocs, Download, see: [Wiki](jackson-module-afterburner/wiki).

## Status

Module is stable and has been used in production environments. Its adoption is not quite as wide as that of
core Jackson components, so rough edges may still exist. However, versions from 2.2 up are considered
production ready.

[![Build Status](https://travis-ci.org/FasterXML/jackson-module-afterburner.svg)](https://travis-ci.org/FasterXML/jackson-module-afterburner)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.module/jackson-module-afterburner/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.module/jackson-module-afterburner/)
[![Javadoc](https://javadoc-emblem.rhcloud.com/doc/com.fasterxml.jackson.module/jackson-module-afterburner/badge.svg)](http://www.javadoc.io/doc/com.fasterxml.jackson.module/jackson-module-afterburner)

## Usage

### Maven dependency

To use module on Maven-based projects, use following dependency:

```xml
<dependency>
  <groupId>com.fasterxml.jackson.module</groupId>
  <artifactId>jackson-module-afterburner</artifactId>
  <version>2.4.0</version>
</dependency>
```

(or whatever version is most up-to-date at the moment)

### Non-Maven

For non-Maven use cases, you download jars from [Central Maven repository](http://repo1.maven.org/maven2/com/fasterxml/jackson/module/jackson-module-afterburner/) or [Download page](jackson-databind/wiki/JacksonDownload).

Databind jar is also a functional OSGi bundle, with proper import/export declarations, so it can be use on OSGi container as is.

### Registering module

To use the the Module in Jackson, simply register it with the ObjectMapper instance:

```java
ObjectMapper mapper = new ObjectMapper()
mapper.registerModule(new AfterburnerModule());
```

after which you just do data-binding as usual:

```java
Value val = mapper.readValue(jsonSource, Value.class);
mapper.writeValue(new File("result.json"), val);
```

### What is optimized?

Following things are optimized:

* For serialization (POJOs to JSON):
 * Accessors for "getting" values (field access, calling getter method) are inlined using generated code instead of reflection
 * Serializers for small number of 'primitive' types (`int`, `long`, String) are replaced with direct calls, instead of getting delegated to `JsonSerializer`s
* For deserialization (JSON to POJOs)
 * Calls to default (no-argument) constructors are byte-generated instead of using reflection
 * Mutators for "setting" values (field access, calling setter method) are inlined using generated code instead of reflection
 * Deserializers for small number of 'primitive' types (`int`, `long`, String) are replaced with direct calls, instead of getting delegated to `JsonDeserializer`s

### ... and what is not?

* Streaming parser/generator access (although it is possible that some optimizations may be added)
* Tree Model: there isn't much that can be done at this level

### More

Check out [Wiki](https://github.com/FasterXML/jackson-module-afterburner/wiki).
