package tools.jackson.module.blackbird.ser;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.Version;

import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.module.blackbird.BlackbirdTestBase;

public class TestStdSerializerOverrides extends BlackbirdTestBase
{
    @JsonPropertyOrder({"a", "b"})
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
                SerializationContext ctxt) {
            gen.writeString("Foo:"+value);
        }
    }

    static class MyIntSerializer extends StdSerializer<Integer>
    {
        public MyIntSerializer() { super(Integer.class); }

        @Override
        public void serialize(Integer value0, JsonGenerator gen,
                SerializationContext ctxt) {
            int v = -value0.intValue();
            gen.writeNumber(v);
        }
    }

    static class MyLongSerializer extends StdSerializer<Long>
    {
        public MyLongSerializer() { super(Long.class); }

        @Override
        public void serialize(Long value0, JsonGenerator gen,
                SerializationContext ctxt) {
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

    private final ObjectMapper VANILLA_MAPPER = newVanillaJSONMapper();
    
    public void testStringSerWith() throws Exception
    {
        ObjectMapper abMapper = newObjectMapper();
        ClassWithPropOverrides input = new ClassWithPropOverrides();
        String jsonPlain = VANILLA_MAPPER.writeValueAsString(input);
        String jsonAb = abMapper.writeValueAsString(input);
        assertEquals(jsonPlain, jsonAb);
    }

    public void testStringSerOverideNoVanilla() throws Exception
    {
        String json = JsonMapper.builder()
                .addModule(new SimpleModule("module", Version.unknownVersion())
                        .addSerializer(String.class, new MyStringSerializer()))
                .build()
                .writeValueAsString(new SimpleStringBean());
        assertEquals("{\"field\":\"Foo:value\"}", json);
    }

    public void testStringSerOverideWithBlackbird() throws Exception
    {
        String json = mapperBuilder()
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

    public void testIntSerOverideVanilla() throws Exception
    {
        // First, baseline, no custom serializer
        assertEquals(aposToQuotes("{'value':42}"),
                VANILLA_MAPPER.writeValueAsString(new SimpleIntBean()));

        // and then with custom serializer, but no BB
        String json = JsonMapper.builder()
                .addModule(new SimpleModule("module", Version.unknownVersion())
                    .addSerializer(Integer.class, new MyIntSerializer())
                    .addSerializer(Integer.TYPE, new MyIntSerializer()))
                .build()
                .writeValueAsString(new SimpleIntBean());
        assertEquals(aposToQuotes("{'value':-42}"), json);
    }

    public void testIntSerOverideWithBlackbird() throws Exception
    {
        String json = mapperBuilder()
                .addModule(new SimpleModule("module", Version.unknownVersion())
                        .addSerializer(Integer.class, new MyIntSerializer())
                        .addSerializer(Integer.TYPE, new MyIntSerializer()))
                    .build()
                    .writeValueAsString(new SimpleIntBean());
                assertEquals(aposToQuotes("{'value':-42}"), json);
    }

    public void testLongSerOverideVanilla() throws Exception
    {
        // First, baseline, no custom serializer
        assertEquals(aposToQuotes("{'value':999}"),
                VANILLA_MAPPER.writeValueAsString(new SimpleLongBean()));

        // and then with custom serializer, but no BB
        String json = JsonMapper.builder()
                .addModule(new SimpleModule("module", Version.unknownVersion())
                    .addSerializer(Long.class, new MyLongSerializer())
                    .addSerializer(Long.TYPE, new MyLongSerializer()))
                .build()
                .writeValueAsString(new SimpleLongBean());
        assertEquals(aposToQuotes("{'value':-999}"), json);
    }

    public void testLongSerOverideWithBlackbird() throws Exception
    {
        String json = mapperBuilder()
                .addModule(new SimpleModule("module", Version.unknownVersion())
                        .addSerializer(Long.class, new MyLongSerializer())
                        .addSerializer(Long.TYPE, new MyLongSerializer()))
                .build()
                .writeValueAsString(new SimpleLongBean());
            assertEquals(aposToQuotes("{'value':-999}"), json);
    }
}
