## Overview

This Jackson extension module provides support for using JAXB (`javax.xml.bind`) annotations as an alternative to native Jackson annotations.
It is most often used to make it easier to reuse existing data beans that used with JAXB framework to read and write XML.

## Maven dependency

To use this extension on Maven-based projects, use following dependency:

```xml
<dependency>
  <groupId>tools.jackson.module</groupId>
  <artifactId>jackson-module-jaxb-annotations</artifactId>
  <version>3.0.0-SNAPSHOT</version>
</dependency>
```

(or whatever version is most up-to-date at the moment)

## Usage

To enable use of JAXB annotations, one must add `JaxbAnnotationIntrospector` provided by this module. There are two ways to do this:

* Register `JaxbAnnotationModule`, or
* Directly add `JaxbAnnotationIntrospector`

Module registration works in standard way:

```java
JaxbAnnotationModule module = new JaxbAnnotationModule();
// configure as necessary
objectMapper.registerModule(module);
```

and the alternative -- explicit configuration is done as:

```java
AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
// if ONLY using JAXB annotations:
mapper.setAnnotationIntrospector(introspector);
// if using BOTH JAXB annotations AND Jackson annotations:
AnnotationIntrospector secondary = new JacksonAnnotationIntrospector();
mapper.setAnnotationIntrospector(new AnnotationIntrospector.Pair(introspector, secondary);
```

Note that by default Module version will use JAXB annotations as the primary, and Jackson annotations as secondary source; but you can change this behavior

(need to add example here!)
