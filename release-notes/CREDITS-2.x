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
