package com.fasterxml.jackson.module.afterburner.format;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

// [databind#1480]
public class BooleanFormatTest extends AfterburnerTestBase
{
    @JsonPropertyOrder({ "b1", "b2", "b3" })
    static class BeanWithBoolean
    {
        @JsonFormat(shape=JsonFormat.Shape.NUMBER)
        public boolean b1;

        @JsonFormat(shape=JsonFormat.Shape.NUMBER)
        public Boolean b2;

        public boolean b3;

        public BeanWithBoolean() { }
        public BeanWithBoolean(boolean b1, Boolean b2, boolean b3) {
            this.b1 = b1;
            this.b2 = b2;
            this.b3 = b3;
        }
    }

    protected static class BooleanWrapper {
        public Boolean b;

        public BooleanWrapper() { }
        public BooleanWrapper(Boolean value) { b = value; }
    }

    static class AltBoolean extends BooleanWrapper
    {
        public AltBoolean() { }
        public AltBoolean(Boolean b) { super(b); }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final static ObjectMapper MAPPER = newAfterburnerMapper();

    public void testShapeViaDefaults() throws Exception
    {
        assertEquals(aposToQuotes("{'b':true}"),
                MAPPER.writeValueAsString(new BooleanWrapper(true)));
        ObjectMapper m = afterburnerMapperBuilder()
                .withConfigOverride(Boolean.class,
                        o -> o.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.NUMBER)))
                .build();
        assertEquals(aposToQuotes("{'b':1}"),
                m.writeValueAsString(new BooleanWrapper(true)));
    }

    public void testShapeOnProperty() throws Exception
    {
        assertEquals(aposToQuotes("{'b1':1,'b2':0,'b3':true}"),
                MAPPER.writeValueAsString(new BeanWithBoolean(true, false, true)));
    }
}
