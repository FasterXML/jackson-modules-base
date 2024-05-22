# jackson-module-subtype

Registering subtypes without annotating the parent class,
see [this](https://github.com/FasterXML/jackson-databind/issues/2104).

Implementation on SPI.

# Usage

Registering modules.

```
ObjectMapper mapper = new ObjectMapper().registerModule(new SubtypeModule());
```

Ensure that the parent class has at least the `JsonTypeInfo` annotation.

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface Parent {
}
```

1. add the `JsonSubType` annotation to your subclass.
2. provide a non-argument constructor (SPI require it).

```java
import com.fasterxml.jackson.module.subtype.JsonSubType;

@JsonSubType("first-child")
public class FirstChild {

    private String foo;
    // ...

    public FirstChild() {
    }
}
```

SPI: Put the subclasses in the `META-INF/services` directory under the interface.
Example: `META-INF/services/package.Parent`

```
package.FirstChild
```

Alternatively, you can also use the `auto-service` to auto-generate these files:

```java
import io.github.black.jackson.JsonSubType;
import com.google.auto.service.AutoService;

@AutoService(Parent.class)
@JsonSubType("first-child")
public class FirstChild {

    private String foo;
    // ...

    public FirstChild() {
    }
}
```

Done, enjoy it.