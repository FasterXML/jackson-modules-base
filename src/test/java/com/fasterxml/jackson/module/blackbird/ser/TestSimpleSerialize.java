package com.fasterxml.jackson.module.blackbird.ser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

public class TestSimpleSerialize extends BlackbirdTestBase
{
    public enum MyEnum {
        A, B, C;
    }

    /* Keep this as package access, since we can't handle private; but
     * public is pretty much always available.
     */
    static class IntBean {
        @JsonProperty("x")
        int getX() { return 123; }
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    static class NonDefaultIntBean {
        private final int _x;

        public NonDefaultIntBean() {
            _x = 123;
        }

        public NonDefaultIntBean(int x) {
            _x = x;
        }

        public long getX() { return _x; }
    }

    public static class LongBean {
        public long getValue() { return -99L; }
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class NonDefaultLongBean {
        private final long _value;

        public NonDefaultLongBean() {
            _value = -99L;
        }

        public NonDefaultLongBean(long value) {
            _value = value;
        }

        public long getValue() { return _value; }
    }

    static class StringBean {
        public String getName() { return "abc"; }
    }

    static class EnumBean {
        public MyEnum getEnum() { return MyEnum.B; }
    }

    static class IntFieldBean {
        @JsonProperty("intF") int x = 17;
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    static class NonDefaultIntFieldBean {
        @JsonProperty("intF")
        int x = 17;

        NonDefaultIntFieldBean() {}

        NonDefaultIntFieldBean(int x) {
            this.x = x;
        }
    }

    static class LongFieldBean {
        @JsonProperty("long") long l = -123L;
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    static class NonDefaultLongFieldBean {
        @JsonProperty("long")
        long l = -123L;

        NonDefaultLongFieldBean() {}

        NonDefaultLongFieldBean(long l) {
            this.l = l;
        }
    }

    public static class StringFieldBean {
        public String foo = "bar";
    }

    public static class EnumFieldBean {
        public MyEnum value = MyEnum.C;
    }

    public static class StringsBean {
        public String a = null;
    }

    @JsonPropertyOrder({ "a", "b" })
    public static class BooleansBean {
        public boolean a = true;
        public boolean getB() { return false; }
    }

    static class CheckGeneratedSerializerName {
        public String stringField;
    }

    /*
    /**********************************************************************
    /* Test methods, method access
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = newObjectMapper();

    public void testIntMethod() throws Exception {
        assertEquals("{\"x\":123}", MAPPER.writeValueAsString(new IntBean()));
    }

    public void testNonDefaultIntMethod() throws Exception {
        assertEquals("{}", MAPPER.writeValueAsString(new NonDefaultIntBean()));
        assertEquals("{\"x\":-181}", MAPPER.writeValueAsString(new NonDefaultIntBean(-181)));
    }

    public void testLongMethod() throws Exception {
        assertEquals("{\"value\":-99}", MAPPER.writeValueAsString(new LongBean()));
    }

    public void testNonDefaultLongMethod() throws Exception {
        assertEquals("{}", MAPPER.writeValueAsString(new NonDefaultLongBean()));
        assertEquals("{\"value\":45}", MAPPER.writeValueAsString(new NonDefaultLongBean(45L)));
    }

    public void testStringMethod() throws Exception {
        assertEquals("{\"name\":\"abc\"}", MAPPER.writeValueAsString(new StringBean()));
    }

    public void testObjectMethod() throws Exception {
        assertEquals("{\"enum\":\"B\"}", MAPPER.writeValueAsString(new EnumBean()));
    }

    /*
    /**********************************************************************
    /* Test methods, field access
    /**********************************************************************
     */

    public void testIntField() throws Exception {
        assertEquals("{\"intF\":17}", MAPPER.writeValueAsString(new IntFieldBean()));
    }

    public void testNonDefaultIntField() throws Exception {
        assertEquals("{}", MAPPER.writeValueAsString(new NonDefaultIntFieldBean()));
        assertEquals("{\"intF\":91}", MAPPER.writeValueAsString(new NonDefaultIntFieldBean(91)));
    }

    public void testLongField() throws Exception {
        assertEquals("{\"long\":-123}", MAPPER.writeValueAsString(new LongFieldBean()));
    }

    public void testNonDefaultLongField() throws Exception {
        assertEquals("{}", MAPPER.writeValueAsString(new NonDefaultLongFieldBean()));
        assertEquals("{\"long\":58}", MAPPER.writeValueAsString(new NonDefaultLongFieldBean(58L)));
    }

    public void testStringField() throws Exception {
        assertEquals("{\"foo\":\"bar\"}", MAPPER.writeValueAsString(new StringFieldBean()));
    }

    public void testStringField2() throws Exception {
        assertEquals("{\"foo\":\"bar\"}", MAPPER.writeValueAsString(new StringFieldBean()));
    }

    public void testObjectField() throws Exception {
        assertEquals("{\"a\":null}", MAPPER.writeValueAsString(new StringsBean()));
    }

    public void testBooleans() throws Exception {
        assertEquals(aposToQuotes("{'a':true,'b':false}"),
                MAPPER.writeValueAsString(new BooleansBean()));
    }

    /*
    /**********************************************************************
    /* Test methods, other
    /**********************************************************************
     */

    public void testFiveMinuteDoc() throws Exception
    {
        ObjectMapper plainMapper = new ObjectMapper();
        ObjectMapper abMapper = MAPPER;
        FiveMinuteUser input = new FiveMinuteUser("First", "Name", true,
                FiveMinuteUser.Gender.FEMALE, new byte[] { 1 } );
        String jsonPlain = plainMapper.writeValueAsString(input);
        String jsonAb = abMapper.writeValueAsString(input);
        assertEquals(jsonPlain, jsonAb);
    }
}
