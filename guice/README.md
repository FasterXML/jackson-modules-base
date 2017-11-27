jackson-module-guice
====================

## Status

[![Build Status](https://travis-ci.org/FasterXML/jackson-module-guice.svg)](https://travis-ci.org/FasterXML/jackson-module-guice)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.module/jackson-module-guice/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.jackson.module/jackson-module-guice/)
[![Javadoc](https://javadoc.io/badge/com.fasterxml.jackson.module/jackson-module-guice.svg)](http://www.javadoc.io/doc/com.fasterxml.jackson.module/jackson-module-guice)

## Documentation

This extension allows Jackson to delegate ObjectMapper creation and value injection to Guice when handling data bindings.
Using the ObjectMapperModule you can register Jackson data binding modules like so:

~~~~~

  Injector injector = Guice.createInjector(
    new ObjectMapperModule().registerModule(new IntegerAsBase16Module())
  );


  public class IntegerAsBase16Module extends SimpleModule
  {
    public IntegerAsBase16Module() {
      super("IntegerAsBase16");

      addSerializer( Integer.class,
          new JsonSerializer<Integer>() {
            @Override
            public void serialize( Integer integer, JsonGenerator jsonGenerator, SerializerProvider serializerProvider )
               throws IOException, JsonProcessingException
            {
              jsonGenerator.writeString(new BigInteger(String.valueOf(integer)).toString(16).toUpperCase());
            }
          }
      );
    }
  }

~~~~~

Subsequently, the ObjectMapper, created from the Guice injector above, will apply the proper data bindings to serialize
Integers as base 16 strings:

~~~~~

  mapper.writeValueAsString(new Integer(10)) ==> "A"

~~~~~

Additional Guice Modules can be used when creating the Injector to automatically inject values into value objects
being de-serialized. The @JacksonInject annotation can be used to trigger Guice driven injection.

Here's an example of a value object where Guice injects three of the members on behalf of Jackson. The first
uses the @JacksonInject annotation, the second uses @JacksonInject with a specific Named binding, and the
third uses @JacksonInject combined with another annotation (@Ann).

~~~~~

  public class SomeBean {
    @JacksonInject
    private int one;

    @JacksonInject
    @Named("two")
    private int two;

    @JacksonInject
    @Ann
    private int three;

    @JsonProperty
    private int four;

    public boolean verify() {
      Assert.assertEquals(1, one);
      Assert.assertEquals(2, two);
      Assert.assertEquals(3, three);
      Assert.assertEquals(4, four);
      return true;
    }
  }

~~~~~

The last, the fourth field, annotated with @JsonProperty uses standard ObjectMapper behavior unlike the other three
which are injected by Guice. The following code snippet demonstrates Guice injection leading to a true return on the
verify() method:


~~~~~

  final Injector injector = Guice.createInjector(
      new ObjectMapperModule(),
      new Module()
      {
        @Override
        public void configure(Binder binder)
        {
          binder.bind(Integer.class).toInstance(1);
          binder.bind(Integer.class).annotatedWith(Names.named("two")).toInstance(2);
          binder.bind(Integer.class).annotatedWith(Ann.class).toInstance(3);
        }
      }
  );

  final ObjectMapper mapper = injector.getInstance(ObjectMapper.class);
  mapper.readValue("{\"four\": 4}", SomeBean.class).verify();

~~~~~

