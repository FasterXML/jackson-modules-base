# jackson-module-blackbird-tests

Classpath-mode integration tests for `jackson-module-blackbird`. **This module is
test-only and is never published.**

## Why this module exists

Blackbird's in-tree tests run as Blackbird's own named JPMS module, so their
test beans live in that module. This module has no `module-info.java`, and its
tests run on the classpath: the test beans live in the unnamed module, as in
most applications. It also holds the tests that need the packaged jar or other
JDKs.

## What is covered

- `inject`: generated codecs engage for setter-based and field-backed POJOs
  in the unnamed module, on the reader and the writer side.
- `diff`: differential tests. Each scenario runs through a stock mapper and a
  mapper with Blackbird, built from the same configuration, and the two must
  give the same value or the same exception (class and message, with the
  reference path). Each scenario then asserts which path Blackbird took: the
  generated codec, a stock property inside it, or the stock (de)serializer for
  the whole bean. The checks look at the (de)serializer that the mapper uses,
  not at the placeholder that a modifier sees. The scenarios cover polymorphic
  values, non-standard creation, property wrappers, ignoral sets, absent
  record components, construction and setter failures, builder setters that
  return null, null serializers, self-references, failures with their
  reference paths, and wide beans.
- `jdk`: the packaged jar. `JdkCompatibilityTest` runs a small program
  against the jar in child JVMs, on the build JDK and on each Java 17-24 home
  listed in `-Dblackbird.test.oldJdks=<home>[,<home>]`: module discovery on the
  class path and module path, with a second module provider on the path, and
  explicit registration with discovery before it, after it and without it.
  Without that property, the old-JDK cases are skipped. On the build JDK it
  also runs discovery and explicit registration with a `jackson-core` that
  does not have a class the generators load. `PackagingTest`
  checks the module descriptor, the OSGi exports and the release guard of the
  root POM. `IsolatedLoaderTest` loads Jackson and Blackbird in a class loader
  that the system class loader cannot see, as an application server or OSGi
  does.

Some checks need the packaged jar (`mvn verify`); a build that stops before the
package phase reads `blackbird/target/classes`, which is not a multi-release
jar, and those checks are skipped.

## Databind test corpus (manual)

The `corpus` directory runs the jackson-databind test suite with
`BlackbirdModule` registered in every mapper that the tests build. Each
failure is a difference between Blackbird and stock databind, or one of the
expected failures that the harness itself causes. The build does not run the
corpus. Run it manually before you submit a change to Blackbird.

The corpus is not part of the build for two reasons. Databind publishes no
test jar, so the run needs a databind source tree. Also, the hook is a patch
to databind main code, which a databind change can break. Thus a databind
change that breaks Blackbird shows only at the next manual run.

### Files

- `databind-register-blackbird.patch`: the only source change to databind.
- `corpus.sh`: the runner. It also compares the failures with the expected
  list.
- `stripmod.py`: copies the Blackbird jar without `module-info.class`.
- `failures.py`: lists the failed tests and the tests with errors from the
  surefire reports.
- `expected-failures.txt`: the expected failures and their cause.

### How to run it

1. Get the jackson-databind source at the version that this checkout
   builds against (the property `jackson.version.databind` in
   `jackson-bom`). For a `-SNAPSHOT` version, use the head of the databind
   branch with the same name as the base branch of your change, for example
   `git clone -b 3.x https://github.com/FasterXML/jackson-databind.git`. For
   a release, use the tag `jackson-databind-<version>`. An older databind
   tree can fail with `NoSuchMethodError` when Blackbird uses newer databind
   API.
2. In the databind tree, apply the patch:
   `git apply <jackson-modules-base>/blackbird-tests/corpus/databind-register-blackbird.patch`.
   If the patch does not apply, update it for the new databind code.
3. In jackson-modules-base, build the Blackbird jar on JDK 25:
   `./mvnw -B -pl blackbird -am package -DskipTests`.
4. Run the corpus on JDK 25 or later:

   ```sh
   JAVA_HOME=<jdk-25> blackbird-tests/corpus/corpus.sh <databind-tree> <out-dir>
   ```

The runner uses the jar in `blackbird/target` of the same checkout. To use a
different jar, give its path as a third argument. To get the baseline
without Blackbird, give `none` as the third argument.

### Expected result

The runner prints `Result: only the expected failures.` and exits with 0.
The expected failures are the tests that assert the exact list or number of
registered modules, because the harness adds one module. The baseline
without Blackbird normally has no failures.

The runner exits with 1 when the failures are not the expected ones, and it
lists each difference. For an unexpected failure, first run the baseline
(`none`) on the same tree: a failure there is a databind failure, not a
Blackbird gap. Otherwise, an unexpected failure is a gap in Blackbird, or a
new databind test that asserts the registered modules. For a gap, fix the
codec, or add a gate that keeps the bean on the stock path. For a new module
test, add it to `expected-failures.txt`. For an expected failure that did not
fail, remove it from that file. The full report of each failure is in
`<out-dir>/failures.txt` and `<out-dir>/reports`.

### What the harness changes in the databind build

The patch adds 10 lines at the start of the `ObjectMapper(MapperBuilder)`
constructor. When the system property `blackbird.corpus` is `true`, the
constructor creates `tools.jackson.module.blackbird.BlackbirdModule` through
reflection and adds it to the builder. Every mapper goes through this
constructor: the public constructors and `build()` (also after `rebuild()`)
call it. Without the property, databind is unchanged.

The runner changes no POM. It gives these settings to Maven:

- `-Dmaven.test.additionalClasspath` adds the Blackbird jar. The runner
  removes `module-info.class` from the jar first, so the jar is the
  automatic module `blackbird.corpus` in the test run.
- `-DargLine` keeps the `--add-opens` of the databind POM, and adds
  `--add-exports` for `deser.impl`, `ser.impl` and `ext.javatime.ser.key`.
  The test descriptor of databind does not have these exports of the main
  descriptor.
- `-Dblackbird.corpus=true` turns on the registration.
- `-Dtools.jackson.module.blackbird.failOnCodegenError=true` makes a failure
  of code generation fail the test, instead of a silent fallback to stock.
- `-Dmaven.test.failure.ignore=true` lets the run continue to the report.

To see the generation decisions, set
`CORPUS_EXTRA_ARGS=-Dblackbird.debug.codegen=true`.

## Do not add a `module-info.java` here

Adding a module descriptor would put the test beans back into a named module
and remove the classpath coverage.

## Do not publish this module

Several parent-POM plugin bindings are unbound or skipped in `pom.xml` so
that `mvn install` on this module produces no jar, no SBOM, no Gradle module
metadata, and no OSGi bundle. See `afterburner-tests/pom.xml` for the same
pattern with extended commentary; this module's `pom.xml` shares the same
shape.
