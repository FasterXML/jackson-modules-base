package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.cfg.GeneratorSettings;
import com.fasterxml.jackson.databind.cfg.SerializationContexts;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.SerializationContextExt;
import com.fasterxml.jackson.databind.ser.SerializerCache;
import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

// Copied from [com.fasterxml.jackson.databind.ser.filter]
public class NullSerializationTest extends AfterburnerTestBase
{
    static class NullSerializer extends ValueSerializer<Object>
    {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider provider)
        {
            gen.writeString("foobar");
        }
    }

    static class Bean1 {
        public String name = null;
    }

    static class Bean2 {
        public String type = null;
    }
    
    @SuppressWarnings("serial")
    static class MyNullSerializerContexts extends SerializationContexts
    {
        public MyNullSerializerContexts() { super(); }
        public MyNullSerializerContexts(TokenStreamFactory tsf, SerializerFactory serializerFactory,
                SerializerCache cache) {
            super(tsf, serializerFactory, cache);
        }

        @Override
        public SerializationContexts forMapper(Object mapper,
                TokenStreamFactory tsf, SerializerFactory serializerFactory,
                SerializerCache cache) {
            return new MyNullSerializerContexts(tsf, serializerFactory, cache);
        }

        @Override
        public SerializationContextExt createContext(SerializationConfig config,
                GeneratorSettings genSettings) {
            return new MyNullSerializerProvider(_streamFactory, _cache,
                    config, genSettings, _serializerFactory);
        }
    }

    static class MyNullSerializerProvider extends SerializationContextExt
    {
        public MyNullSerializerProvider(TokenStreamFactory streamFactory,
                SerializerCache cache, SerializationConfig config,
                GeneratorSettings genSettings, SerializerFactory f) {
            super(streamFactory, config, genSettings, f, cache);
        }

        @Override
        public ValueSerializer<Object> findNullValueSerializer(BeanProperty property)
        {
            if ("name".equals(property.getName())) {
                return new NullSerializer();
            }
            return super.findNullValueSerializer(property);
        }
    }

    static class BeanWithNullProps
    {
        @JsonSerialize(nullsUsing=NullSerializer.class)
        public String a = null;
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = newAfterburnerMapper();

    public void testSimple() throws Exception
    {
        assertEquals("null", MAPPER.writeValueAsString(null));
    }

    public void testOverriddenDefaultNulls() throws Exception
    {
        ObjectMapper m = afterburnerMapperBuilder()
                .addModule(new SimpleModule()
                        .setDefaultNullValueSerializer(new NullSerializer())
                        )
                .build();
        assertEquals("\"foobar\"", m.writeValueAsString(null));
    }

    public void testCustomPOJONullsViaProvider() throws Exception
    {
        ObjectMapper m = afterburnerMapperBuilder()
                .serializationContexts(new MyNullSerializerContexts())
                .build();
        assertEquals("{\"name\":\"foobar\"}", m.writeValueAsString(new Bean1()));
        assertEquals("{\"type\":null}", m.writeValueAsString(new Bean2()));
    }

    public void testCustomTreeNullsViaProvider() throws Exception
    {
        ObjectNode root = MAPPER.createObjectNode();
        root.putNull("a");

        // by default, null is... well, null
        assertEquals("{\"a\":null}", MAPPER.writeValueAsString(root));

        // but then we can customize it
        ObjectMapper m = afterburnerMapperBuilder()
                .addModule(new SimpleModule()
                        .setDefaultNullValueSerializer(new NullSerializer()))
                .serializationContexts(new MyNullSerializerContexts())
                .build();
        assertEquals("{\"a\":\"foobar\"}", m.writeValueAsString(root));
    }

    public void testNullSerializeViaPropertyAnnotation() throws Exception
    {
        assertEquals("{\"a\":\"foobar\"}",
                MAPPER.writeValueAsString(new BeanWithNullProps()));
    }
}
