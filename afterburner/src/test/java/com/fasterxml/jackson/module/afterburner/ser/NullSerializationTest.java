package com.fasterxml.jackson.module.afterburner.ser;

import java.io.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.cfg.CacheProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

// Copied from [com.fasterxml.jackson.databind.ser.filter]
public class NullSerializationTest extends AfterburnerTestBase
{
    static class NullSerializer extends JsonSerializer<Object>
    {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider provider)
            throws IOException
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
    static class MyNullProvider extends DefaultSerializerProvider
    {
        public MyNullProvider() { super(); }
        public MyNullProvider(MyNullProvider base, SerializationConfig config, SerializerFactory jsf) {
            super(base, config, jsf);
        }

        @Override // @since 2.16
        public DefaultSerializerProvider withCaches(CacheProvider cacheProvider) {
            throw new UnsupportedOperationException("No cache configuration supported");
        }
        
        // not really a proper impl, but has to do
        @Override
        public DefaultSerializerProvider copy() {
            return this;
        }
        
        @Override
        public DefaultSerializerProvider createInstance(SerializationConfig config, SerializerFactory jsf) {
            return new MyNullProvider(this, config, jsf);
        }

        @Override
        public JsonSerializer<Object> findNullValueSerializer(BeanProperty property)
            throws JsonMappingException
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

    private final ObjectMapper MAPPER = newObjectMapper();

    @Test
    public void testSimple() throws Exception
    {
        assertEquals("null", MAPPER.writeValueAsString(null));
    }

    @Test
    public void testOverriddenDefaultNulls() throws Exception
    {
        DefaultSerializerProvider sp = new DefaultSerializerProvider.Impl();
        sp.setNullValueSerializer(new NullSerializer());
        ObjectMapper m = new ObjectMapper();
        m.setSerializerProvider(sp);
        assertEquals("\"foobar\"", m.writeValueAsString(null));
    }

    @Test
    public void testCustomPOJONullsViaProvider() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        m.setSerializerProvider(new MyNullProvider());
        assertEquals("{\"name\":\"foobar\"}", m.writeValueAsString(new Bean1()));
        assertEquals("{\"type\":null}", m.writeValueAsString(new Bean2()));
    }

    @Test
    public void testCustomTreeNullsViaProvider() throws Exception
    {
        ObjectNode root = MAPPER.createObjectNode();
        root.putNull("a");

        // by default, null is... well, null
        assertEquals("{\"a\":null}", MAPPER.writeValueAsString(root));

        // but then we can customize it:
        DefaultSerializerProvider prov = new MyNullProvider();
        prov.setNullValueSerializer(new NullSerializer());
        ObjectMapper m = new ObjectMapper();
        m.setSerializerProvider(prov);
        assertEquals("{\"a\":\"foobar\"}", m.writeValueAsString(root));
    }

    @Test
    public void testNullSerializeViaPropertyAnnotation() throws Exception
    {
        assertEquals("{\"a\":\"foobar\"}", MAPPER.writeValueAsString(new BeanWithNullProps()));
    }
}
