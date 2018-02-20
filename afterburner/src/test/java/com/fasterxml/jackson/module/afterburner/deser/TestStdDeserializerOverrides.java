package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

@SuppressWarnings("serial")
public class TestStdDeserializerOverrides extends AfterburnerTestBase
{
    static class ClassWithPropOverrides
    {
        public String a;
        
        @JsonDeserialize(using=MyStringDeserializer.class)
        public String b;
    }

    static class MyStringDeserializer extends StdDeserializer<String>
    {
        public MyStringDeserializer() { super(String.class); }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            return "Foo:"+p.getText();
        }
    }

    // for [module-afterburner#59]
    static class Issue59Bean {
        public String field;
    }

    static class DeAmpDeserializer extends StdDeserializer<String>
    {
        public DeAmpDeserializer() { super(String.class); }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return p.getText().replaceAll("&amp;", "&");
        }
    }
    
    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    public void testFiveMinuteDoc() throws Exception
    {
        ObjectMapper plainMapper = new ObjectMapper();
        ObjectMapper abMapper = newAfterburnerMapper();
        final String JSON = "{\"a\":\"a\",\"b\":\"b\"}";
        
        ClassWithPropOverrides vanilla = plainMapper.readValue(JSON, ClassWithPropOverrides.class);
        ClassWithPropOverrides burnt = abMapper.readValue(JSON, ClassWithPropOverrides.class);
        
        assertEquals("a", vanilla.a);
        assertEquals("Foo:b", vanilla.b);
        
        assertEquals("a", burnt.a);
        assertEquals("Foo:b", burnt.b);
    }

    public void testStringDeserOverideNoAfterburner() throws Exception
    {
        final String json = "{\"field\": \"value &amp; value\"}";
        final String EXP = "value & value";
        Issue59Bean resultVanilla = ObjectMapper.builder()
                .addModule(new SimpleModule("module", Version.unknownVersion())
                        .addDeserializer(String.class, new DeAmpDeserializer()))
                .build()
                .readValue(json, Issue59Bean.class);
        assertEquals(EXP, resultVanilla.field);
    }

    // for [module-afterburner#59]
    public void testStringDeserOverideWithAfterburner() throws Exception
    {
        final String json = "{\"field\": \"value &amp; value\"}";
        final String EXP = "value & value";

        final SimpleModule module = new SimpleModule("module", Version.unknownVersion()) {
            @Override
            public void setupModule(SetupContext context) {
                context.addDeserializers(
                        new Deserializers.Base() {
                            @Override
                            public JsonDeserializer<?> findBeanDeserializer(
                                    JavaType type,
                                    DeserializationConfig config,
                                    BeanDescription beanDesc)
                                    throws JsonMappingException {
                                if (type.hasRawClass(String.class)) {
                                    return new DeAmpDeserializer();
                                }
                                return null;
                            }
                        });
            }
        };
        
        // but then fails with Afterburner
        Issue59Bean resultAB = afterburnerMapperBuilder()
                .addModule(module)
                .build()
                .readValue(json, Issue59Bean.class);
        assertEquals(EXP, resultAB.field);
    }
}
