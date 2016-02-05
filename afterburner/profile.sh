#!/bin/sh

java -Xmx64m -server -cp lib/\*:target/\*:target/test-classes \
 -Xrunhprof:cpu=samples,depth=10,verbose=n,interval=2 \
$*
