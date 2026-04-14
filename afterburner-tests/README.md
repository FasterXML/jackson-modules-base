# jackson-module-afterburner-tests

Classpath-mode integration tests for `jackson-module-afterburner`. **This module is
test-only and is never published.**

## Why this module exists

Afterburner's most valuable optimizations come from bytecode injection:
`PropertyMutatorCollector`, `PropertyAccessorCollector`, and `CreatorOptimizer`
generate custom mutator / accessor / instantiator classes in the *target bean's
package* and install them on the bean's `ClassLoader`. This only works when the
target package is not sealed.

In Jackson 3.x every module descriptor — including afterburner's own test module
— is a named JPMS module, and the JVM automatically seals every package inside a
named module. As a result, every POJO defined inside `tools.jackson.module.afterburner`
test sources fails the `MyClassLoader.canAddClassInPackageOf` check, and afterburner
silently falls back to plain reflection. That means **none** of afterburner's
injection paths are exercised by the in-tree afterburner test suite — the optimized
code path is effectively dead weight in CI even though the tests appear to pass.

This module closes that gap. It lives alongside the afterburner module but
deliberately:

- has **no `module-info.java`** in either main or test sources,
- is configured (`useModulePath=false`) to run its tests on the classpath,
- and therefore loads its test POJOs into the **unnamed module**, whose packages
  are not sealed.

Afterburner's injection pipeline runs on those POJOs normally, and the tests here
verify via reflection on databind internals that each property was actually
replaced with an `OptimizedSettableBeanProperty` / `OptimizedBeanPropertyWriter`
and each default-creator POJO got an `OptimizedValueInstantiator`.

## Do not add a `module-info.java` here

Adding a module descriptor would put these test POJOs back into a named module,
re-seal their packages, and silently turn this whole module into dead weight —
same as the in-tree afterburner tests.

If a future cleanup pass wants to "unify" the project layout by making every
module JPMS-consistent, this one is the exception. Read this file before doing
that.

## Do not publish this module

Several parent-POM plugin bindings are unbound or skipped in `pom.xml` so that
`mvn install` on this module produces no jar, no SBOM, no Gradle module metadata,
and no OSGi bundle. The module exists purely to run tests. If you rename an
execution override in `pom.xml`, re-verify with:

```
./mvnw help:effective-pom -pl afterburner-tests
```

A typo in an execution id turns into silent dead config and leaves stale
artifacts in `target/` rather than a build error.

## What is covered

- `PropertyMutatorCollector` — int, long, boolean, and reference-type
  specializations for both public-field and setter-method access
  (`MutatorSpecializationsTest`).
- `PropertyAccessorCollector` — writer replacement on the serializer side
  (`SerializerInjectionTest`).
- `CreatorOptimizer` — default-constructor and static-factory positive cases,
  plus property-based `@JsonCreator` negative case (`CreatorOptimizerTest`).
- `MyClassLoader.defineClass` — transitively exercised by all of the above.

## What is *not* covered

- Private-class guard (`_classLoader != null && isPrivate(beanClass)`).
- `setUseValueClassLoader(false)` toggle.
- Concurrent deserializer construction.
- Fallback path when afterburner correctly refuses to optimize (e.g. a bean
  whose package really is sealed — the in-tree afterburner tests inadvertently
  cover this).
- GraalVM native-image disable path in `AfterburnerModule.setupModule`.

## Known findings from this module

### Afterburner parent-classloader caching silently broken on Java 9+ (#348)

While writing `GeneratedClassCachingTest`, we discovered that Afterburner's
parent-classloader cache — the mechanism that's supposed to make two
independent `ObjectMapper` instances share a single generated mutator class
per POJO — does not work on Java 9+ unless the JVM is launched with
`--add-opens java.base/java.lang=ALL-UNNAMED`. `MyClassLoader` reflects into
`ClassLoader#findLoadedClass` and `ClassLoader#defineClass`, both protected
members of `java.lang.ClassLoader`; on modern JDKs the reflective access
throws `InaccessibleObjectException`, which Afterburner catches silently and
falls back to defining each generated class in a fresh throwaway loader.

That's why this module's `pom.xml` sets
`--add-opens java.base/java.lang=ALL-UNNAMED` on the surefire `argLine`.
Without it, `testSameBeanAcrossMappersReusesSameMutatorClass` fails with two
distinct `Class<?>` instances carrying identical fully-qualified names — the
exact fingerprint of the leak. This is **not a test-environment quirk**; it
affects every real Afterburner user on Java 9+. See issue #348 for the fix
plan.

## References

- PR #347: rationale for the module's structure and initial coverage
- Issue #348: afterburner classloader caching regression (surfaced here)
