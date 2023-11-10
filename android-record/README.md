Module that allows deserialization into records on Android, 
where java records are supported through desugaring, 
and Jackson's built-in support for records doesn't work, 
since the desugared classes have a non-standard super class, 
and record component-related reflection methods are missing.

See [Android Developers Blog article](https://android-developers.googleblog.com/2023/06/records-in-android-studio-flamingo.html)

Note: this module is a no-op when no Android-desugared records are being deserialized,
so it is safe to use in code shared between Android and non-Android platforms.

## Usage

Functionality can be used by registering the module and then just deserializing things
using regular API:

```java
ObjectMapper mapper = JsonMapper.builder() // or mapper for other dataformats
  .addModule(new AndroidRecordModule())
    // add other modules, configure, etc
    .build();
```

Maven information for jar is:

* Group id: `com.fasterxml.jackson.module`
* Artifact id: `jackson-module-android-record`

## Other

For Javadocs, Download, see: [Wiki](../../wiki).
