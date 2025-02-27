package com.fasterxml.jackson.module.guice7;

import java.io.IOException;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectMapperModuleTest
{
    @Test
    public void testJacksonInjectThroughGuice() throws Exception
    {
        final Injector injector = Guice.createInjector(
                new ObjectMapperModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(Integer.class).toInstance(1);
                        // guice based named injection
                        binder.bind(Integer.class).annotatedWith(Names.named("two")).toInstance(2);
                        binder.bind(Integer.class).annotatedWith(Ann.class).toInstance(3);
                        // jakarta based named injection
                        binder.bind(Integer.class).annotatedWith(Names.named("five")).toInstance(5);
                        // guice based method injection
                        binder.bind(Integer.class).annotatedWith(Names.named("six")).toInstance(6);
                        // jakarta based method injection
                        binder.bind(Integer.class).annotatedWith(Names.named("seven")).toInstance(7);
                        // test other method injections (need different keys, so use Long
                        binder.bind(Long.class).annotatedWith(Ann.class).toInstance(8L);
                        binder.bind(Long.class).toInstance(9L);
                    }
                }
        );

        final ObjectMapper mapper = injector.getInstance(ObjectMapper.class);

        mapper.readValue("{\"four\": 4}", SomeBean.class).verify();
    }

    @Test
    public void testModulesRegisteredThroughNormalInstantiation() throws Exception
    {
        final Injector injector = Guice.createInjector(
                new ObjectMapperModule().registerModule(new IntegerAsBase16Module())
        );

        final ObjectMapper mapper = injector.getInstance(ObjectMapper.class);

        assertEquals(mapper.writeValueAsString(10), "\"A\"");
    }

    @Test
    public void testModulesRegisteredThroughInjection() throws Exception
    {
        final Injector injector = Guice.createInjector(
                new ObjectMapperModule().registerModule(IntegerAsBase16Module.class)
        );

        final ObjectMapper mapper = injector.getInstance(ObjectMapper.class);

        assertEquals(mapper.writeValueAsString(10), "\"A\"");
    }

    @Test
    public void testModulesRegisteredThroughInjectionWithAnnotation() throws Exception
    {
        final Injector injector = Guice.createInjector(
                new ObjectMapperModule().registerModule(IntegerAsBase16Module.class, Ann.class),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(IntegerAsBase16Module.class).annotatedWith(Ann.class).to(IntegerAsBase16Module.class);
                    }
                }
        );

        final ObjectMapper mapper = injector.getInstance(ObjectMapper.class);

        assertEquals(mapper.writeValueAsString(10), "\"A\"");
    }

    @Test
    public void testModulesRegisteredThroughInjectionWithNameAnnotation() throws Exception
    {
        final Injector injector = Guice.createInjector(
                new ObjectMapperModule().registerModule(IntegerAsBase16Module.class, Names.named("billy")),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(IntegerAsBase16Module.class)
                                .annotatedWith(Names.named("billy"))
                                .to(IntegerAsBase16Module.class);
                    }
                }
        );

        final ObjectMapper mapper = injector.getInstance(ObjectMapper.class);

        assertEquals(mapper.writeValueAsString(10), "\"A\"");
    }

    @Test
    public void testModulesRegisteredThroughInjectionWithKey() throws Exception
    {
        final Injector injector = Guice.createInjector(
                new ObjectMapperModule().registerModule(Key.get(IntegerAsBase16Module.class))
        );

        final ObjectMapper mapper = injector.getInstance(ObjectMapper.class);

        assertEquals(mapper.writeValueAsString(10), "\"A\"");
    }

    private static class SomeBean
    {
        @JacksonInject
        private int one;

        @JacksonInject
        @com.google.inject.name.Named("two")
        private int two;

        @JacksonInject
        @Ann
        private int three;

        @JsonProperty
        private int four;

        @JacksonInject
        @jakarta.inject.Named("five")
        private int five;

        // Those will be injected by methods
        private int six;
        private int seven;
        private long eight;
        private long nine;

        @JacksonInject
        private void injectSix(@com.google.inject.name.Named("six") int s)
        {
            this.six = s;
        }

        @JacksonInject
        private void injectSeven(@jakarta.inject.Named("seven") int s)
        {
            this.seven = s;
        }

        @JacksonInject
        private void injectEight(@Ann long e)
        {
            this.eight = e;
        }

        @JacksonInject
        private void injectNine(long n)
        {
            this.nine = n;
        }

        public boolean verify()
        {
            assertEquals(1, one);
            assertEquals(2, two);
            assertEquals(3, three);
            assertEquals(4, four);
            assertEquals(5, five);
            assertEquals(6, six);
            assertEquals(7, seven);
            assertEquals(8, eight);
            assertEquals(9, nine);
            return true;
        }

    }

    @SuppressWarnings("serial")
    static class IntegerAsBase16Module extends SimpleModule
    {
        public IntegerAsBase16Module() {
            super("IntegerAsBase16");

            addSerializer(
                    Integer.class,
                    new JsonSerializer<Integer>()
                    {
                        @Override
                        public void serialize(
                                Integer integer, JsonGenerator jsonGenerator, SerializerProvider serializerProvider
                        ) throws IOException, JsonProcessingException
                        {
                            jsonGenerator.writeString(new BigInteger(String.valueOf(integer)).toString(16).toUpperCase());
                        }
                    }
            );
        }
    }
}
