Module that allows instantiation of Java POJOs that do not have "default constructor"
(constructor that takes no arguments) but should be deserialized from JSON Object
as POJOs. In such cases, module tries to use

    sun.reflect.ReflectionFactory

to force instantiation that by-passes all constructors (it is the mechanism used by
JDK serialization system).

## Usage

Functionality can be used by registering the module and then just deserializing things
using regular API:
current introspector:

```java
ObjectMapper mapper = JsonMapper.builder() // or mapper for other dataformats
    .addModule(new NoCtorDeserModule())
    // add other modules, configure, etc
    .build();
```

Maven information for jar is:

* Group id: `com.fasterxml.jackson.module`
* Artifact id: `jackson-module-no-ctor-deser`

## Other

For Javadocs, Download, see: [Wiki](../../wiki).
