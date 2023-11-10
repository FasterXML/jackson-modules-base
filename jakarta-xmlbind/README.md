## Overview

This Jackson extension module provides support for using Jakarta Java XML Binding
(`jakarta.xml.bind`) annotations as an alternative to native Jackson annotations.

It is most often used to make it easier to reuse existing data beans that used with
Jakarta XmlBind framework to read and write XML.

NOTE! This module was added in Jackson 2.13 to support NEW version 3.0 API of existing "JAXB",
after "old" `javax.xml.` package had to be repackaged as "Jakarta" variant.
For older `java.xml.bind` package, look at "jackson-module-jaxb-annotations".

## Maven dependency

To use this extension on Maven-based projects, use following dependency:

```xml
<dependency>
  <groupId>tools.jackson.module</groupId>
  <artifactId>jackson-module-jakarta-xmlbind-annotations</artifactId>
  <version>3.0.0-SNAPSHOT</version>
</dependency>
```

(or whatever version is most up-to-date at the moment)

## Usage

To enable use of Jakarta XmlBin annotations, one must add `JakartaXmlBindAnnotationIntrospector` provided
by this module. There are two ways to do this:

* Register `JakartaXmlBindAnnotationModule`, OR
* Directly add `JakartaXmlBindAnnotationIntrospector` for use by `ObjectMapper`

Module registration works in standard way:

```java
JakartaXmlBindAnnotationModule module = new JakartaXmlBindAnnotationModule();
// configure as necessary
objectMapper.registerModule(module);
```

and the alternative -- explicit configuration is done as:

```java
AnnotationIntrospector introspector = new JakartaXmlBindAnnotationIntrospector();
// if ONLY using XmlBind annotations:
mapper.setAnnotationIntrospector(introspector);
// if using BOTH XmlBin annotations AND Jackson annotations:
AnnotationIntrospector secondary = new JacksonAnnotationIntrospector();
mapper.setAnnotationIntrospector(new AnnotationIntrospector.Pair(introspector, secondary);
```

Note that by default Module version will use XmlBind annotations as the primary,
and Jackson annotations as secondary source; but you can change this behavior
