Project: jackson-modules-base
Modules:
  jackson-module-guice
  jackson-module-jaxb
  jackson-module-mrbean
  jackson-module-osgi
  jackson-module-paranamer

------------------------------------------------------------------------
=== Releases ===
------------------------------------------------------------------------

2.10.0.pr2 (31-Aug-2019)

#84: Add expand entity protection and secure processing to DomElementJsonDeserializer
 (reported by Bartosz B)

2.10.0.pr1 (19-Jul-2019)

#52: (mrbean) Interfaces may have non-abstract methods (since java8)
 (suggested by Harrison H)
#79: (all) Add simple module-info for JDK9+, using Moditect
- Update `asm` version 5.2 -> 7.0 for JDK 11 support
- Remove SPI metadata for OSGi module as there is no 0-arg constructor, does
  not work

2.9.9 (16-May-2019)

#49: Afterburner `MyClassLoader#loadAndResolve()` is not idempotent when
  `tryToUseParent` is true
 (reported, fix contributed by Jeffrey B)
#69: `ALLOW_COERCION_OF_SCALARS` ignored deserializing scalars with Afterburner
 (reported, fix contributed by Dan S)
#74: MrBean module should not materialize `java.io.Serializable`
 (reported by Georg S-D)

2.9.8 (15-Dec-2018)

No changes since 2.9.7

2.9.7 (19-Sep-2018)

#54: Afterburner` SuperSonicBeanDeserializer` does not handle JSON Object
  valued Object Ids (like json)
 (reported by William H)

2.9.6 (12-Jun-2018)

#44: (jaxb) `@XmlElements` does not work with `@XmlAccessorType(XmlAccessType.NONE)`
 (reported by Alexander O)
- (afterburner) Cleaning up issues wrt null-skipping/handling (wrt `@JsonSetter(nulls)`

2.9.5 (26-Mar-2018)

#42: NPE from MrBean when `get()` or `set()` is though as property
 (reported by Tuomas K)

2.9.4 (24-Jan-2018)

#38: (afterburner) Handle DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES correctly
 (reported by Qnzvna@github)

2.9.3 (09-Dec-2017)

#31: (jaxb) `@JsonAppend` causes 'IllegalStateException` `Unsupported annotated member'
  with `JaxbAnnotationModule`
 (reported by asodja@github)
#32: (jaxb) Fix introspector chaining in `JaxbAnnotationIntrospector.hasRequiredMarker()`
 (reported by Vojtěch H)
#33: (afterburner) `@JsonSerialize` with `nullUsing` option not working for `String` properties

2.9.2 (14-Oct-2017)

#30: (afterburner) `IncompatibleClassChangeError` deserializing interface
  methods with default impl
 (reported by Shawn S)
#33: (afterburner) `@JsonSerialize` with `nullUsing` option not working for `String` properties

2.9.1 (07-Sep-2017)

No changes since 2.9.0

2.9.0 (30-Jul-2017)

- (afterburner) Simplified generation of mutators, to remove one level of
  indirecton (simpler code, potential minor performance improvement)
- Add `jackson-module-jaxb-annotations` as one more base module!

2.8.10 (24-Aug-2017)
2.8.9 (12-Jun-2017)
2.8.8 (05-Apr-2017)
2.8.7 (21-Feb-2017)
2.8.6 (12-Jan-2017)
2.8.5 (14-Nov-2016)

No changes since 2.8.4.

2.8.4 (14-Oct-2016)

#22: (guice) Allow use of Guice 4.x (still only require 3.x) #22
 (suggested by Sadayuki F)

2.8.3 (18-Sep-2016)

[afterburner] Add override for `SettableBeanProperty.fixAccess()` introduced
 in 2.8.3 of `jackson-databind` (internal method, not part of public API)

2.8.2 (30-Aug-2016)
2.8.1 (20-Jul-2016)

No changes since 2.8.0.

2.8.0 (04-Jul-2016)

#6: (guice) ObjectMapperModule doesn't allow configuring JsonFactory to use
 (reported by bogdan-neoworks@github)
#16 (afterburner) Fixed typo of generated serializer name
 (reported, proposed fix by tacoo@github)

2.7.6 (not yet released)

#7 (afterburner) Afterburner excludes serialization of some `null`-valued Object properties
 (reported by David H)
#12: (mrbean) Problem deserializing Long into Calendar with mrBean
 (reported by Kyle L)
#13: (paranamer) Make `ParanamerAnnotationIntrospector` serializable

2.7.5 (11-Jun-2016)

No changes since 2.7.4

2.7.4 (29-Apr-2016)

#4: (afterburner) Serialization with Afterburner causes Java VerifyError for
  generic with String as type
 (reported by imfuraisoth@github)
#8 (mrbean): Problem with `Optional<T>`, `AtomicReference<T>` valued properties
 (reported by Thomas V)
- Upgrade to Asm 5.1 for mr-bean, afterburner

2.7.3 (16-Mar-2016)
2.7.2 (26-Feb-2016)
2.7.1 (02-Feb-2016)

No changes since 2.7.0.


