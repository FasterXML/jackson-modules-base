# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Jackson modules-base is a multi-module Maven project containing 11 foundational Jackson 3 extension modules that build directly on jackson-databind. Each module is independent (no inter-module dependencies).

**Modules:** afterburner, blackbird, mrbean, guice, guice7, jaxb, jakarta-xmlbind, osgi, android-record, no-ctor-deser, spi-subtypes

(paranamer exists in the tree but is not built for 3.x — it is commented out of the root `pom.xml`.)

Maven coordinates: groupId `tools.jackson.module`, parent `tools.jackson:jackson-base`; depends on `tools.jackson.core:jackson-databind`.

## Build Commands

```bash
# Full build (all modules)
./mvnw -B -q -ff -ntp verify

# Build a single module
./mvnw -B -q -ff -ntp verify -pl afterburner

# Run tests only (all modules)
./mvnw -B -ff -ntp test

# Run tests for a single module
./mvnw -B -ff -ntp test -pl blackbird

# Run a single test class
./mvnw -B -ff -ntp test -pl afterburner -Dtest=TestSimpleSerialize

# Run a single test method
./mvnw -B -ff -ntp test -pl afterburner -Dtest=BasicDeserializeTest#testIntMethod
```

Java 17 is the minimum (`javac.src.version` 17); CI tests against 17, 21, 24. osgi adds `--add-opens java.base/java.lang=ALL-UNNAMED` to its test `argLine`.

## Architecture

Every module follows the same pattern:
- A `*Module` class (e.g., `AfterburnerModule`, `BlackbirdModule`) extends Jackson's `tools.jackson.databind.JacksonModule`, registered on a mapper builder (e.g., `JsonMapper.builder().addModule(...)`)
- Package: `tools.jackson.module.<name>` (hyphens dropped or turned into sub-packages: `androidrecord`, `noctordeser`, `spisubtypes`, `jakarta.xmlbind`)
- `PackageVersion.java` is generated from `PackageVersion.java.in` by the replacer Maven plugin — do not edit manually
- JPMS module descriptors are real sources: `<module>/src/main/java/module-info.java` (and `src/test/java/module-info.java` for tests). Moditect is no longer used; stray `src/moditect/` dirs in blackbird and android-record are leftovers
- SPI registration: `src/main/resources/META-INF/services/tools.jackson.databind.JacksonModule` (all modules except guice, guice7, osgi)

### Key module categories

- **Performance (bytecode generation):** afterburner (Byte Buddy-based), blackbird (LambdaMetafactory-based, modern replacement) — both disable themselves automatically in GraalVM native images via `NativeImageUtil.isInNativeImage()` check in `setupModule`
- **DI integration:** guice (javax.inject), guice7 (jakarta.inject) — note: guice's `ObjectMapperModule` implements Guice's `com.google.inject.Module`, not Jackson's `JacksonModule`
- **Type materialization:** mrbean (generates implementations for interfaces/abstract classes, Byte Buddy-based)
- **Annotation support:** jaxb (javax.xml.bind), jakarta-xmlbind (jakarta.xml.bind)
- **Other:** osgi (OSGi service injection), android-record (Android desugared records), no-ctor-deser (no default constructor), spi-subtypes (SPI-based subtype registration)

### Dependencies

Unlike 2.x, nothing is shaded: afterburner and mrbean depend on `net.bytebuddy:byte-buddy` (version managed in the root pom as `version.bytebuddy`) as a regular dependency. ASM appears only as a test dependency.

## Testing

- **Framework:** JUnit 5 (Jupiter)
- **Base test classes:** Each module has its own (e.g., `AfterburnerTestBase`, `BlackbirdTestBase`, `BaseJaxbTest`, `BaseTest`) providing mapper construction helpers and shared test types
- **Test location:** `<module>/src/test/java/tools/jackson/module/<name>/`
- Tests use static imports from `org.junit.jupiter.api.Assertions`
- **Mapper construction in tests:** Use `mapperBuilder()` (returns `JsonMapper.Builder` with module pre-registered) or `newObjectMapper()` factory methods from the test base class; `newVanillaJSONMapper()` gives a baseline mapper without the module
- **Test helpers:** `q("str")` wraps in double-quotes, `a2q("{'a':1}")` converts single to double quotes; assertion helpers `verifyException()`, `assertToken()`
- **SPI test:** `ModuleSPIMetadataTest` verifies discovery via `ServiceLoader.load(JacksonModule.class)`; present in afterburner, android-record, blackbird, jakarta-xmlbind, mrbean, no-ctor-deser, spi-subtypes (not yet in jaxb, which is registered)

## Release & Branching

- **Main branches:** `3.x` (Jackson 3 development, default branch) and `2.x` (head of the 2.x line); there is no `master`
- **Development branches:** per-minor-version branches (e.g., `3.0`, `3.1`, `3.2`; `2.21`, `2.22`); fixes merge forward (e.g., `2.21` → `2.22` → `2.x` → `3.1` → `3.2` → `3.x`)
- **Current version (this branch, `3.1`):** 3.1.8-SNAPSHOT
- Releases use maven-release-plugin; snapshots deploy to Sonatype Central Portal
