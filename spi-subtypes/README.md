# jackson-module-spi-subtypes

Registering subtypes without annotating the parent class,
see [this](https://github.com/FasterXML/jackson-databind/issues/2104).

Implementation on SPI.

# Usage

Registering modules.

```
ObjectMapper mapper = JsonMapper.builder()
    .addModule(new SubtypesModule())
    .build();
```

Ensure that the parent class has at least the `JsonTypeInfo` annotation.

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface Parent {
}
```

1. add the `JacksonSubType` annotation to your subclass.
2. provide a non-argument constructor (SPI requires it).

```java
package org.example;

import com.fasterxml.jackson.module.spisubtypes.JacksonSubType;

@JacksonSubType("first-child")
public class FirstChild {

    private String foo;
    // ...

    public FirstChild() {
    }
}
```

SPI: Put the subclasses in the `META-INF/services` directory under the interface.
Example: `META-INF/services/org.example.Parent`

```
org.example.FirstChild
```

Alternatively, you can also use the `auto-service` to auto-generate these files:

```java
package org.example;

import com.fasterxml.jackson.module.spisubtypes.JacksonSubType;
import com.google.auto.service.AutoService;

@AutoService(Parent.class)
@JacksonSubType("first-child")
public class FirstChild {

    private String foo;
    // ...

    public FirstChild() {
    }
}
```

Done, enjoy it.
