Module that allows (de)serialization of records using the canonical constructor and accessors on Android,
where java records are supported through desugaring, and Jackson's built-in support for records doesn't work,
since the desugared classes have a non-standard super class,
and record component-related reflection methods are missing.

See [Android Developers Blog article](https://android-developers.googleblog.com/2023/06/records-in-android-studio-flamingo.html)

An attempt was made to make this module as consistent with Jackson's built-in support for records as possible,
but gaps exist when using some of Jackson's advanced mapping features.

Note: this module is a no-op when no Android-desugared records are being (de)serialized,
so it is safe to use in code shared between Android and non-Android platforms.

Note: the canonical record constructor is identified through matching of parameter names and types with fields.
Therefore, this module doesn't allow a deserialized desugared record to have a custom constructor
with the same set of parameter names and types as the canonical one.
For the same reason, this module requires that desugared canonical record constructor parameter names
be stored in class files. Apparently, with Android SDK 34 tooling, that is the case by default.
If that ever changes, it may require an explicit setting in build files.

Known limitation: this module replaces the default `ClassIntrospector`, 
so it cannot be used together with any other module that does the same.

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

* Group id: `tools.jackson.module`
* Artifact id: `jackson-module-android-record`

## Other

For Javadocs, Download, see: [Wiki](../../wiki).
