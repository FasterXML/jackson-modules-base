## Overview

This Jackson extension module provides support for using Jakarta Java XML Binding
(`jakarta.xml.bind`) annotations as an alternative to native Jackson annotations.

It is most often used to make it easier to reuse existing data beans that used with
JAXB framework to read and write XML.

NOTE! This module was added in Jackson 2.13 to support NEW version 3.0 API of JAXB,
after "old" `javax.xml.` package had to be repackaged as "Jakarta" variant.
For older `java.xml.bind` package, look at "jackson-module-jaxb-annotations".

## Maven dependency

To use this extension on Maven-based projects, use following dependency:

```xml
<dependency>
  <groupId>com.fasterxml.jackson.module</groupId>
  <artifactId>jackson-module-jakarta-jaxb-annotations</artifactId>
  <version>2.13.0</version>
</dependency>
```

(or whatever version is most up-to-date at the moment)

## Usage

To enable use of JAXB annotations, one must add `JakartaJaxbAnnotationIntrospector` provided
by this module. There are two ways to do this:

* Register `JakartaJaxbAnnotationModule`, OR
* Directly add `JakartaJaxbAnnotationIntrospector` for use by `ObjectMapper`

Module registration works in standard way:

```java
JakartaJaxbAnnotationModule module = new JakartaJaxbAnnotationModule();
// configure as necessary
objectMapper.registerModule(module);
```

and the alternative -- explicit configuration is done as:

```java
AnnotationIntrospector introspector = new JakartaJaxbAnnotationIntrospector();
// if ONLY using JAXB annotations:
mapper.setAnnotationIntrospector(introspector);
// if using BOTH JAXB annotations AND Jackson annotations:
AnnotationIntrospector secondary = new JacksonAnnotationIntrospector();
mapper.setAnnotationIntrospector(new AnnotationIntrospector.Pair(introspector, secondary);
```

Note that by default Module version will use JAXB annotations as the primary,
and Jackson annotations as secondary source; but you can change this behavior
