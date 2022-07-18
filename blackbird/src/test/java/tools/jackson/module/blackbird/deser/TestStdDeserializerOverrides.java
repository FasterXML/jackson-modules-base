package tools.jackson.module.blackbird.deser;

import tools.jackson.core.*;

import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.Deserializers;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;

@SuppressWarnings("serial")
public class TestStdDeserializerOverrides extends BlackbirdTestBase
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
        public String deserialize(JsonParser p, DeserializationContext ctxt) {
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
        public String deserialize(JsonParser p, DeserializationContext ctxt) {
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
        ObjectMapper abMapper = newObjectMapper();
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
        Issue59Bean resultVanilla = JsonMapper.builder()
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
                            public ValueDeserializer<?> findBeanDeserializer(
                                    JavaType type,
                                    DeserializationConfig config,
                                    BeanDescription beanDesc) {
                                if (type.hasRawClass(String.class)) {
                                    return new DeAmpDeserializer();
                                }
                                return null;
                            }

                            @Override
                            public boolean hasDeserializerFor(DeserializationConfig config,
                                    Class<?> valueType) {
                                return false;
                            }
                        });
            }
        };

        // but then fails with Blackbird
        Issue59Bean resultAB = mapperBuilder()
                .addModule(module)
                .build()
            .readValue(json, Issue59Bean.class);
        assertEquals(EXP, resultAB.field);
    }
}
