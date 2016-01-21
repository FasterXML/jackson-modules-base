## Overview

This is a multi-module umbrella project for [Jackson](../../../jackson)
modules that are considered foundational, building on core databind, but
not including datatype or data format modules, or JAX-RS providers.
Not all "general" modules are included here; this grouping is to be used
for more mature (and generally slower moving, stable) modules.

Currently included are:

* [Mr Bean](mrbean/)
* [OSGi](osgi/)

and more are likely to be moved during Jackson 2.7 maintenance cycle.

All modules are licensed under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt).

## Status

[![Build Status](https://travis-ci.org/FasterXML/jackson-base-modules.svg)](https://travis-ci.org/FasterXML/jackson-base-modules)

## More

See [Wiki](../../wiki) for more information (javadocs).
