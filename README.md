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
* [JAXB Annotations](jaxb/)
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

A note on compatibility of JAXB annotations module, Jakarta 3.0 API libraries for JAXB: with Jackson 2.12,
Jakarta versions can be referenced for the JAXB module by using the classifier "jakarta" in your dependency

```
<dependency>
    <groupId>com.fasterxml.jackson.jaxrs</groupId>
    <artifactId>jackson-module-jaxb-annotations</artifactId>
    <classifier>jakarta</classifier>
</dependency>
``` 

## More

See [Wiki](../../wiki) for more information (javadocs).
