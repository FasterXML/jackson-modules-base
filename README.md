## Overview

This is a multi-module umbrella project for [Jackson](../../../jackson)
modules that are considered foundational, building on core databind, but
not including datatype or data format modules, or JAX-RS providers.
Not all "general" modules are included here; this grouping is to be used
for more mature (and generally slower moving, stable) modules.

Currently included are:

* [Afterburner](afterburner/)
* [Blackbird](blackbird/) (NEW in 2.12!)
* [Guice](guice/)
* Java XML Binding Annotation compatibility
    * "Old" (`java.xml.bind`) annotations: [JAXB Annotations](jaxb/)
    * New "Jakarta" (`jakarta.xml.bind`): [Jakarta XML Bind Annotations(jakarta-xmlbind/) (added in 2.13)
* [Mr Bean](mrbean/)
* [OSGi](osgi/)
* [Paranamer](paranamer/)

## Status

[![Build Status](https://travis-ci.org/FasterXML/jackson-modules-base.svg)](https://travis-ci.org/FasterXML/jackson-modules-base)

## License

All modules are licensed under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt).

Additionally, 2.x versions of `Afterburner` and `Mr Bean` use [ASM](https://gitlab.ow2.org/asm/asm),
licensed as per:

    https://asm.ow2.io/license.html

whereas 3.0 will use [ByteBuddy](https://github.com/raphw/byte-buddy) (licensed as per https://github.com/raphw/byte-buddy/blob/master/LICENSE)

## Using Jakarta

### Jackson 2.13 and later (once released)

With 2.13, you need to choose either:

* `jackson-module-jaxb-annotations` for "old JAXB" (2.x): supports `javax.xml.bind` annotations
* `jackson-module-jakarta-xmlbind-annotations` for "new Jakarta JAXB" (3.x): supports `jakarta.xml.bind` annotations

(in theory you can even use both, with databind `AnnotationIntrospectorPair`, but more often you will only want one of these)

Note that Jakarta version was added in Jackson 2.13 and was not available for earlier versions.

### Jackson 2.12 (only)

Alternatively if using Jackson 2.12, there is a specific variant of `jackson-module-jaxb-annotations`
available, usable with Maven classifier of "jakarta". You can use it instead of "old" JAXB variant
by specifying classifier like so:

```
<dependency>
    <groupId>com.fasterxml.jackson.jaxrs</groupId>
    <artifactId>jackson-module-jaxb-annotations</artifactId>
    <classifier>jakarta</classifier>
</dependency>
``` 

## More

See [Wiki](../../wiki) for more information (javadocs).
