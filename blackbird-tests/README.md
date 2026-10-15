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
  Without that property, the old-JDK cases are skipped. `PackagingTest`
  checks the module descriptor, the OSGi exports and the release guard of the
  root POM. `IsolatedLoaderTest` loads Jackson and Blackbird in a class loader
  that the system class loader cannot see, as an application server or OSGi
  does.

Some checks need the packaged jar (`mvn verify`); a build that stops before the
package phase reads `blackbird/target/classes`, which is not a multi-release
jar, and those checks are skipped.

## Do not add a `module-info.java` here

Adding a module descriptor would put the test beans back into a named module
and remove the classpath coverage.

## Do not publish this module

Several parent-POM plugin bindings are unbound or skipped in `pom.xml` so
that `mvn install` on this module produces no jar, no SBOM, no Gradle module
metadata, and no OSGi bundle. See `afterburner-tests/pom.xml` for the same
pattern with extended commentary; this module's `pom.xml` shares the same
shape.
