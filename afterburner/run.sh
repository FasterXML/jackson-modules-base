#!/bin/sh

java -Xmx64m -server -cp lib/\*:target/\*:target/test-classes $*

