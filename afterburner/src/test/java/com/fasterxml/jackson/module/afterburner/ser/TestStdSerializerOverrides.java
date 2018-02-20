package com.fasterxml.jackson.module.afterburner.ser;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

@SuppressWarnings("serial")
public class TestStdSerializerOverrides extends AfterburnerTestBase
{
    static class ClassWithPropOverrides
    {
        public String a = "a";
        
        @JsonSerialize(using=MyStringSerializer.class)
        public String b = "b";
    }

    static class MyStringSerializer extends StdSerializer<String>
    {
        public MyStringSerializer() { super(String.class); }

        @Override
        public void serialize(String value, JsonGenerator gen,
                SerializerProvider provider) throws IOException {
            gen.writeString("Foo:"+value);
        }
    }

    static class MyIntSerializer extends StdSerializer<Integer>
    {
        public MyIntSerializer() { super(Integer.class); }

        @Override
        public void serialize(Integer value0, JsonGenerator gen,
                SerializerProvider provider) throws IOException {
            int v = -value0.intValue();
            gen.writeNumber(v);
        }
    }

    static class MyLongSerializer extends StdSerializer<Long>
    {
        public MyLongSerializer() { super(Long.class); }

        @Override
        public void serialize(Long value0, JsonGenerator gen,
                SerializerProvider provider) throws IOException {
            long v = -value0.longValue();
            gen.writeNumber(v);
        }
    }
    
    // for [module-afterburner#59]
    static class SimpleStringBean {
        public String field = "value";
    }

    static class SimpleIntBean {
        public int getValue() { return 42; }
    }

    static class SimpleLongBean {
        public long value = 999L;
    }
    
    /*
    /**********************************************************************
    /* Test methods; String overrides
    /**********************************************************************
     */

    private final ObjectMapper VANILLA_MAPPER = new ObjectMapper();
    
    public void testStringSerWith() throws Exception
    {
        ObjectMapper plainMapper = new ObjectMapper();
        ObjectMapper abMapper = newAfterburnerMapper();
        ClassWithPropOverrides input = new ClassWithPropOverrides();
        String jsonPlain = plainMapper.writeValueAsString(input);
        String jsonAb = abMapper.writeValueAsString(input);
        assertEquals(jsonPlain, jsonAb);
    }

    public void testStringSerOverideNoAfterburner() throws Exception
    {
        String json = ObjectMapper.builder()
                .addModule(new SimpleModule("module", Version.unknownVersion())
                        .addSerializer(String.class, new MyStringSerializer()))
                .build()
                .writeValueAsString(new SimpleStringBean());
        assertEquals("{\"field\":\"Foo:value\"}", json);
    }

    public void testStringSerOverideWithAfterburner() throws Exception
    {
        String json = afterburnerMapperBuilder()
            .addModule(new SimpleModule("module", Version.unknownVersion())
                .addSerializer(String.class, new MyStringSerializer()))
            .build()
            .writeValueAsString(new SimpleStringBean());
        assertEquals("{\"field\":\"Foo:value\"}", json);
    }

    /*
    /**********************************************************************
    /* Test methods; numbers overrides
    /**********************************************************************
     */

    public void testIntSerOverideNoAfterburner() throws Exception
    {
        // First, baseline, no custom serializer
        assertEquals(aposToQuotes("{'value':42}"),
                VANILLA_MAPPER.writeValueAsString(new SimpleIntBean()));

        // and then with custom serializer, but no Afterburner
        String json = ObjectMapper.builder()
                .addModule(new SimpleModule("module", Version.unknownVersion())
                    .addSerializer(Integer.class, new MyIntSerializer())
                    .addSerializer(Integer.TYPE, new MyIntSerializer()))
                .build()
                .writeValueAsString(new SimpleIntBean());
        assertEquals(aposToQuotes("{'value':-42}"), json);
    }

    public void testIntSerOverideWithAfterburner() throws Exception
    {
        String json = afterburnerMapperBuilder()
            .addModule(new SimpleModule("module", Version.unknownVersion())
                .addSerializer(Integer.class, new MyIntSerializer())
                .addSerializer(Integer.TYPE, new MyIntSerializer()))
            .build()
            .writeValueAsString(new SimpleIntBean());
        assertEquals(aposToQuotes("{'value':-42}"), json);
    }

    public void testLongSerOverideNoAfterburner() throws Exception
    {
        // First, baseline, no custom serializer
        assertEquals(aposToQuotes("{'value':999}"),
                VANILLA_MAPPER.writeValueAsString(new SimpleLongBean()));

        // and then with custom serializer, but no Afterburner
        String json = ObjectMapper.builder()
                .addModule(new SimpleModule("module", Version.unknownVersion())
                    .addSerializer(Long.class, new MyLongSerializer())
                    .addSerializer(Long.TYPE, new MyLongSerializer()))
                .build()
                .writeValueAsString(new SimpleLongBean());
        assertEquals(aposToQuotes("{'value':-999}"), json);
    }

    public void testLongSerOverideWithAfterburner() throws Exception
    {
        String json = afterburnerMapperBuilder()
            .addModule(new SimpleModule("module", Version.unknownVersion())
                    .addSerializer(Long.class, new MyLongSerializer())
                    .addSerializer(Long.TYPE, new MyLongSerializer()))
            .build()
            .writeValueAsString(new SimpleLongBean());
        assertEquals(aposToQuotes("{'value':-999}"), json);
    }
}
