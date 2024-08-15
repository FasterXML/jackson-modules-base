Here are people who have contributed to development Jackson JSON processor
Base modules
(version numbers in brackets indicate release in which the problem was fixed)
(NOTE: incomplete -- need to collect info from sub-projects, pre-2.9)

Authors:

Tatu Saloranta (tatu.saloranta@iki.fi): author
Steven Schlansker (stevenschlansker@github): author of Blackbird module (2.12)
LinJunhua (linlinnn@github): author of No-constructor-deserialization module (2.13)

Other contributors:

Vojtěch Habarta (vojtechhabarta@gitub)

* Reported [JAXB#32]: Fix introspector chaining in `JaxbAnnotationIntrospector.hasRequiredMarker()`
 (2.9.3)

Tuomas Kiviaho (TuomasKiviaho@github)

* Reported #42: NPE from MrBean when `get()` or `set()` is though as property
 (2.9.5)

Alexander Onnikov (aonnikov@github)

* Reported #44: (jaxb) `@XmlElements` does not work with `@XmlAccessorType(XmlAccessType.NONE)`
 (2.9.6)

William Headrick (headw01@github)

* Reported 54: Afterburner` SuperSonicBeanDeserializer` does not handle JSON Object
  valued Object Ids (like json)
 (2.9.7)

Jeffrey Bagdis (jbagdis@github)

* Reported, contributed fix to #49: Afterburner `MyClassLoader#loadAndResolve()`
  is not idempotent when `tryToUseParent` is true
 (2.9.9)

Dan Sănduleac (dansanduleac@github)
 
* Reported, contributed fix for #69: `ALLOW_COERCION_OF_SCALARS` ignored deserializing scalars
   with Afterburner
 (2.9.9)

Georg Schmidt-Dumont (georgschmidtdumont@github)

* Reported #74: MrBean module should not materialize `java.io.Serializable`
 (2.9.9)

Jeffrey Bagdis (jbagdis@github)

* Reported, contributed fix for #49: Afterburner `MyClassLoader#loadAndResolve()`
  is not idempotent when `tryToUseParent` is true
 (2.9.9)

Harrison Houghton (hrhino@github)

* Suggested, contributed impl for #52: Interfaces may have non-abstract methods (since java8)
 (2.10.0)

Bartosz Baranowski (baranowb@github)

* Reported #84: (jaxb) Add expand entity protection and secure processing to
  DomElementJsonDeserializer
 (2.10.0)

Robby Morgan (robbytx@github)

* Contributed #109: (mrbean) Fix detection of inherited default method in Java 8+ interface
 (2.11.3)
* Contributed #110: (mrbean) Avoid generating implementations of synthetic bridge methods
 (2.11.3)


Steven Schlansker (stevenschlansker@github)

* Contributed #85: Add Blackbird module -- alternative for Afterburner that works better with
   new(er) JVMs
 (2.12.0)
* Fixed #141: Blackbird fails to deserialize varargs array
 (2.13.0)
... and many, many more (esp. to Afterburner/Blackbird projects)

Marc Magon (GedMarc@github)

* Contributed #116: (jaxb) Jakarta Namespace Support
 (2.12.0)

Carter Kozak (carterkozak@github)

* Reported #120: Afterburner does not support the new CoercionConfig
 (2.12.1)

LinJunhua (linlinnn@github)

* Contributed #140: Add new module jackson-module-no-ctor-deser which supports
  no-default-constructor POJOs
 (2.13.0)

Alexey Gavrilov (agavrilov76@github)

* Reported #161: Module name in `jakarta-xmlbind/src/moditect/module-info.java`
  is invalid
 (2.13.2)

David Connard (davidconnard@github)

* Reported #169: Blackbird fails with LinkageError when the same class is used
  across two separate classloaders
 (2.13.3)

Joe Barnett (josephlbarnett@github)

* Contributed fix for #169: Blackbird fails with LinkageError when the same class
  is used across two separate classloaders
 (2.13.3)
* Contributed #209: Add guice7 (jakarta.inject) module
 (2.16.0)

James R. Perkins (jamezp@github)
* Contributed fix for #175: `jackson-module-jakarta-xmlbind-annotations` should use
  a Jakarta namespaced Activation API
 (2.13.4)

Aleksandr Beliakov (bsanchezb@github)

* Contributed #157: Bumb jakarta.activation-api dependency from 1.2.1 to 1.2.2
 (2.14.0)

René Scheibe (darxriggs@github)

* Suggested #187: Remove stack trace from Blackbirds warnings wrt missing `MethodHandles.lookup()`
  (on Java 8)
 (2.14.0)

Marco Descher (@col-panic)

* Contributed fix for #219: (jakarta-xmlbind) Using `jackson-module-jakarta-xmlbind-annotations`
  2.15.2 fails in OSGi Environment with JAXB 4
 (2.16.0)

Eran Leshem (@eranl)

* Contributed #227: Add `jackson-module-android-record
 (2.16.0)

Sammy Chu (@sammyhk)

* Reported #231: (jakarta-xmlbind) Missed change of `javax.activation;resolution:=optional`
  to `jakarta.activation;resolution:=optional` in `jakarta-xmlbind/pom.xml`
 (2.16.1)

Jack Dunning (@JDUNNIN)

* Contributed #233: (jaxb) Tolerate JAX-RS 2.2 in jackson-module-jaxb-annotations so
  that it can be deployed in Liberty alongside features which use 2.2
 (2.18.0)

@HelloOO7

* Contributed #248: (android-record) jClass annotations and polymorphic types are ignored
  when deserializing Android Record fields
  (2.18.0)
