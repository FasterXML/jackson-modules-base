# Jackson OSGi injection module

This module provides a way to inject OSGI services into deserialized objects.
Thanks to the _JacksonInject_ annotations, the _OsgiJacksonModule_ will search for the required service in the OSGI service registry and injects it in the object while deserializing.

Module is licensed under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)

## Status

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.module/jackson-module-osgi/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.module/jackson-module-osgi/)
[![Javadoc](https://javadoc.io/badge/com.fasterxml.jackson.module/jackson-module-osgi.svg)](http://www.javadoc.io/doc/com.fasterxml.jackson.module/jackson-module-osgi)

## Usage

For example, imagine a drawing software that persists shapes in JSON documents (on file system, mongodb or orientdb). The _Shape_ object needs a _DrawingService_ that is in charge of drawing shapes.

```java
interface Drawable {
    void draw();
}

interface DrawingService {
    void draw(Drawable drawable);
}
	
class Shape implements Drawable {
    public int x;
    public int y;

    private DrawingService drawingService;

    public Shape(@JacksonInject DrawingService drawingService)
    {
        this.drawingService = drawingService;
    }

    @Override
    public void draw()
    {
        drawingService.draw(this);
    }
}
```

To deserialize shapes and to inject the drawing service :

```java
ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new OsgiJacksonModule(bundleContext));
Shape shape = mapper.reader().forType(Shape.class).readValue("{\"x\":13,\"y\":21}");
```

The module supports OSGI filters to select the service more accurately :

```java
public Shape(@JacksonInject(value = "(provider=ACME)") DrawingService drawingService)
{
    this.drawingService = drawingService;
}
```

## Limitations

* injecting value in setter is not supported
* dynamicity is not supported. If the service is unregistered, the deserialized object will keep the old service reference.
