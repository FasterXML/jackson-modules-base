# jackson-module-blackbird-tests

Classpath-mode integration tests for `jackson-module-blackbird`. **This module is
test-only and is never published.**

## Why this module exists

This module is the Blackbird counterpart to
[`afterburner-tests`](../afterburner-tests/README.md). The coverage gap it
addresses is narrower than Afterburner's, because Blackbird's optimizer
mechanism is structurally different — and better aligned with JPMS — than
Afterburner's.

**Afterburner:** uses ByteBuddy + `ClassLoader.defineClass` + reflective
access to `java.lang.ClassLoader` methods. On Java 9+, reflective access to
`ClassLoader` is gated by JPMS, and in the JPMS-named test module every test
POJO ends up in a sealed package. That combination silently disables the
optimizer in the in-tree afterburner test suite. See the `afterburner-tests`
README for the full story.

**Blackbird:** uses `MethodHandles.Lookup.defineClass` (a JDK 9+ supported
API) + `MethodHandles.privateLookupIn`. Neither needs reflective access to
`ClassLoader` methods, neither cares whether the target package is "sealed by
module", and `privateLookupIn` works across the JPMS module boundary whenever
the target module opens its package to the caller (or lives in the unnamed
module). As a result, Blackbird's in-tree tests **do** exercise the optimizer
for setter-based POJOs.

What's NOT exercised by Blackbird's in-tree tests, and is covered here:

- **Classpath / unnamed-module POJOs.** A real-world POJO loaded by the
  system class loader rather than as part of the Blackbird JPMS module.
  This module's test POJOs live in the unnamed module (no `module-info.java`
  anywhere), so the optimizer has to cross the module boundary.
- **The documented limitations of Blackbird's optimizer** — specifically
  that direct public-field access is **not** optimized on either the
  deserializer or serializer side. Blackbird's
  `BBDeserializerModifier.nextProperty` and
  `BBSerializerModifier.createProperty` both skip non-method members.
- **The `CrossLoaderAccess` fast-path behavior.** Blackbird's
  `CrossLoaderAccess` contains a slow-path that defines a
  `$$JacksonBlackbirdAccess` companion class via `Lookup.defineClass(byte[])`
  to upgrade a partial-privilege lookup. `CrossLoaderAccessTest` pins the
  current behavior that on JDK 9+ with an unnamed-module bean, the fast
  path always wins and the companion class is never defined.

## Do not add a `module-info.java` here

Same reason as `afterburner-tests`: adding a module descriptor would put the
test POJOs back into a named module and change how `privateLookupIn`
resolves them, silently undermining the classpath-coverage purpose.

## Do not publish this module

Several parent-POM plugin bindings are unbound or skipped in `pom.xml` so
that `mvn install` on this module produces no jar, no SBOM, no Gradle module
metadata, and no OSGi bundle. See `afterburner-tests/pom.xml` for the same
pattern with extended commentary; this module's `pom.xml` shares the same
shape.

## What is covered

- `BBDeserializerModifier` — setter specializations (int / long / boolean /
  String / Object) in `SetterOptimizationTest`.
- `BBSerializerModifier` — getter-based writer specializations in
  `SerializerInjectionTest`.
- Field-access negative cases (known design limitation) on both sides, in
  `FieldAccessNotOptimizedTest` and `SerializerInjectionTest`.
- `CrossLoaderAccess` fast-path short-circuit for unnamed-module beans, in
  `CrossLoaderAccessTest`.

## What is *not* covered

- Private-class guard (`Modifier.isPrivate(beanClass)` in both modifiers).
- The slow-path `CrossLoaderAccess.accessClassIn` companion-class definition
  — that code is effectively dead for classpath POJOs on JDK 9+; see
  `CrossLoaderAccessTest` javadoc for the explanation.
- `CreatorOptimizer` — Blackbird has one; a follow-up could mirror
  `afterburner-tests/CreatorOptimizerTest`.
- Concurrent deserializer construction.
- Bean classes in a JPMS module other than Blackbird's own. Testing that
  would require a third submodule with its own `module-info.java` that
  `opens` its package to Blackbird, which adds complexity for unclear
  additional value.
- Lambda-classloader growth as a function of mapper count. Blackbird's
  fundamental design creates a new anonymous lambda class per
  `LambdaMetafactory.metafactory` invocation; this isn't a bug and isn't
  caching-related, so there's nothing to assert.

## References

- PR that introduced this module — see git history
- [`afterburner-tests/README.md`](../afterburner-tests/README.md) for the
  sibling module and the broader rationale
- Issue #348 — analogous (but different) afterburner classloader caching
  issue. Does not apply to Blackbird: Blackbird uses `Lookup.defineClass`,
  not reflective `ClassLoader.defineClass`, so it is structurally immune.
