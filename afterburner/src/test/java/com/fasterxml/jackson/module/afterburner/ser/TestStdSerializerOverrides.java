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

public class TestStdSerializerOverrides extends AfterburnerTestBase
{
    static class ClassWithPropOverrides
    {
        public String a = "a";
        
        @JsonSerialize(using=MyStringSerializer.class)
        public String b = "b";
    }

    @SuppressWarnings("serial")
    static class MyStringSerializer extends StdSerializer<String>
    {
        public MyStringSerializer() { super(String.class); }

        @Override
        public void serialize(String value, JsonGenerator gen,
                SerializerProvider provider) throws IOException {
            gen.writeString("Foo:"+value);
        }
    }

    // for [module-afterburner#59]
    static class FooBean {
        public String field = "value";
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    public void testFiveMinuteDoc() throws Exception
    {
        ObjectMapper plainMapper = new ObjectMapper();
        ObjectMapper abMapper = mapperWithModule();
        ClassWithPropOverrides input = new ClassWithPropOverrides();
        String jsonPlain = plainMapper.writeValueAsString(input);
        String jsonAb = abMapper.writeValueAsString(input);
        assertEquals(jsonPlain, jsonAb);
    }

    public void testStringSerOverideNoAfterburner() throws Exception
    {
        final FooBean input = new FooBean();
        final String EXP = "{\"field\":\"Foo:value\"}";
        String json = new ObjectMapper()
            .registerModule(new SimpleModule("module", Version.unknownVersion())
                .addSerializer(String.class, new MyStringSerializer()))
            .writeValueAsString(input);
        assertEquals(EXP, json);
    }

    public void testStringSerOverideWithAfterburner() throws Exception
    {
        final FooBean input = new FooBean();
        final String EXP = "{\"field\":\"Foo:value\"}";
        String json = mapperWithModule()
            .registerModule(new SimpleModule("module", Version.unknownVersion())
                .addSerializer(String.class, new MyStringSerializer()))
            .writeValueAsString(input);
        assertEquals(EXP, json);
    }
}
