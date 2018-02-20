package com.fasterxml.jackson.module.afterburner.deser;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Vector;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class TestSimpleDeserialize extends AfterburnerTestBase
{
    public enum MyEnum {
        A, B, C;
    }
    
    /* Keep this as package access, since we can't handle private; but
     * public is pretty much always available.
     */
    static class IntBean {
        protected int _x;
        
        void setX(int v) { _x = v; }
    }

    @JsonPropertyOrder({"c","a","b","e","d"})
    static class IntsBean {
        protected int _a, _b, _c, _d, _e;
        
        void setA(int v) { _a = v; }
        void setB(int v) { _b = v; }
        void setC(int v) { _c = v; }
        void setD(int v) { _d = v; }
        void setE(int v) { _e = v; }
    }
    
    public static class LongBean {
        protected long _x;
        
        public void setX(long v) { _x = v; }
    }

    public static class StringBean {
        protected String _x;
        
        public void setX(String v) { _x = v; }
    }

    public static class EnumBean {
        protected MyEnum _x;
        
        public void setX(MyEnum v) { _x = v; }
    }
    
    public static class IntFieldBean {
        @JsonProperty("value") int x;
    }
    static class LongFieldBean {
        public long value;
    }
    static class StringFieldBean {
        public String x;
    }
    static class EnumFieldBean {
        public MyEnum x;
    }

    static class StringAsObject {
        public Object value;
    }

    static class BooleansBean {
        public boolean a;

        protected Boolean _b;

        public void setB(boolean b) {
            _b = b;
        }
    }
    
    @JsonPropertyOrder
    ({"stringField", "string", "intField", "int", "longField", "long", "enumField", "enum"})
    static class MixedBean {
        public String stringField;
        public int intField;
        public long longField;
        public MyEnum enumField;

        protected String stringMethod;
        protected int intMethod;
        protected long longMethod;
        protected MyEnum enumMethod;

        public void setInt(int i) { intMethod = i; }
        public void setLong(long l) { longMethod = l; }
        public void setString(String s) { stringMethod = s; }
        public void setEnum(MyEnum e) { enumMethod = e; }
    }

    static class BeanWithNonVoidPropertySetter {
        private String stringField;
        private String stringField2;

        public String getStringField() { return stringField; }
        public String getStringField2() { return stringField2; }

        public BeanWithNonVoidPropertySetter setStringField(String username) {
            this.stringField = username;
            return this;
        }

        public BeanWithNonVoidPropertySetter setStringField2(String stringField2) {
            this.stringField2 = stringField2;
            return this;
        }
    }

    static class BigBeanWithNonVoidPropertySetter {
        private String stringField;

        public String getStringField() { return stringField; }

        public BigBeanWithNonVoidPropertySetter setStringField(String username) {
            this.stringField = username;
            return this;
        }

        public BigBeanWithNonVoidPropertySetter setBogus1(String bogus) { return this; }
        public BigBeanWithNonVoidPropertySetter setBogus2(String bogus) { return this; }
        public BigBeanWithNonVoidPropertySetter setBogus3(String bogus) { return this; }
        public BigBeanWithNonVoidPropertySetter setBogus4(String bogus) { return this; }
        public BigBeanWithNonVoidPropertySetter setBogus5(String bogus) { return this; }

        public String getBogus1() { return ""; }
        public String getBogus2() { return ""; }
        public String getBogus3() { return ""; }
        public String getBogus4() { return ""; }
        public String getBogus5() { return ""; }
    }

    // for [module-afterburner#60]
    static class Issue60Pojo {
        public List<Object> foos;
    }    

    static class CheckGeneratedDeserializerName {
        public String stringField;
    }

    /*
    /**********************************************************************
    /* Test methods, method access
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = newAfterburnerMapper();
    
    public void testIntMethod() throws Exception {
        IntBean bean = MAPPER.readValue("{\"x\":13}", IntBean.class);
        assertEquals(13, bean._x);
    }

    public void testMultiIntMethod() throws Exception {
        IntsBean bean = MAPPER.readValue("{\"c\":3,\"a\":9,\"b\":111,\"e\":-9999,\"d\":1}", IntsBean.class);
        assertEquals(9, bean._a);
        assertEquals(111, bean._b);
        assertEquals(3, bean._c);
        assertEquals(1, bean._d);
        assertEquals(-9999, bean._e);
    }
    
    public void testLongMethod() throws Exception {
        LongBean bean = MAPPER.readValue("{\"x\":-1}", LongBean.class);
        assertEquals(-1, bean._x);
    }

    public void testStringMethod() throws Exception {
        StringBean bean = MAPPER.readValue("{\"x\":\"zoobar\"}", StringBean.class);
        assertEquals("zoobar", bean._x);
    }

    public void testObjectMethod() throws Exception {
        EnumBean bean = MAPPER.readValue("{\"x\":\"A\"}", EnumBean.class);
        assertEquals(MyEnum.A, bean._x);
    }
    
    /*
    /**********************************************************************
    /* Test methods, field access
    /**********************************************************************
     */

    public void testIntField() throws Exception {
        IntFieldBean bean = MAPPER.readValue("{\"value\":-92}", IntFieldBean.class);
        assertEquals(-92, bean.x);
    }

    public void testLongField() throws Exception {
        LongFieldBean bean = MAPPER.readValue("{\"value\":-92}", LongFieldBean.class);
        assertEquals(-92, bean.value);
    }

    public void testStringField() throws Exception {
        StringFieldBean bean = MAPPER.readValue("{\"x\":\"\"}", StringFieldBean.class);
        assertEquals("", bean.x);

        // also, null handling:
        bean = MAPPER.readValue("{\"x\":null}", StringFieldBean.class);
        assertNull(bean.x);
    }

    public void testEnumField() throws Exception {
        EnumFieldBean bean = MAPPER.readValue("{\"x\":\"C\"}", EnumFieldBean.class);
        assertEquals(MyEnum.C, bean.x);
    }

    // Verify [Issue#10], so that nulls do not get coerced to String "null"
    public void testStringAsObjectField() throws Exception {
        StringAsObject bean = MAPPER.readValue("{\"value\":null}", StringAsObject.class);
        assertNotNull(bean);
        assertNull(bean.value);
    }
    
    /*
    /**********************************************************************
    /* Test methods, other
    /**********************************************************************
     */

    public void testFiveMinuteDoc() throws Exception
    {
        FiveMinuteUser input = new FiveMinuteUser("First", "Name", true,
                FiveMinuteUser.Gender.FEMALE, new byte[] { 1 } );
        String jsonAb = MAPPER.writeValueAsString(input);

        FiveMinuteUser output = MAPPER.readValue(jsonAb, FiveMinuteUser.class);
        if (!output.equals(input)) {
            fail("Round-trip test failed: intermediate JSON = "+jsonAb);
        }
    }
    
    public void testMixed() throws Exception
    {
        MixedBean bean = MAPPER.readValue("{"
                +"\"stringField\":\"a\","
                +"\"string\":\"b\","
                +"\"intField\":3,"
                +"\"int\":4,"
                +"\"longField\":-3,"
                +"\"long\":11,"
                +"\"enumField\":\"A\","
                +"\"enum\":\"B\""
                +"}", MixedBean.class);

        assertEquals("a", bean.stringField);
        assertEquals("b", bean.stringMethod);
        assertEquals(3, bean.intField);
        assertEquals(4, bean.intMethod);
        assertEquals(-3L, bean.longField);
        assertEquals(11L, bean.longMethod);
        assertEquals(MyEnum.A, bean.enumField);
        assertEquals(MyEnum.B, bean.enumMethod);
    }

    // Test for [Issue-5]
    public void testNonVoidProperty() throws Exception
    {
        final String json = "{ \"stringField\" : \"zoobar\", \"stringField2\" : \"barzoo\" }";

        BeanWithNonVoidPropertySetter bean = MAPPER.readValue(json, BeanWithNonVoidPropertySetter.class);
        assertEquals("zoobar", bean.getStringField());

        ObjectMapper abMapper = newAfterburnerMapper();
        // current fails with java.lang.NoSuchMethodError
        bean = abMapper.readValue(json, BeanWithNonVoidPropertySetter.class);
        assertEquals("zoobar", bean.getStringField());
        assertEquals("barzoo", bean.getStringField2());
    }

    // Test for [module-afterburner#16]
    public void testBigNonVoidProperty() throws Exception
    {
        final String json = "{ \"stringField\" : \"zoobar\" }";

        BigBeanWithNonVoidPropertySetter bean = MAPPER.readValue(json, BigBeanWithNonVoidPropertySetter.class);
        assertEquals("zoobar", bean.getStringField());

        ObjectMapper abMapper = newAfterburnerMapper();
        // current fails with java.lang.NoSuchMethodError
        bean = abMapper.readValue(json, BigBeanWithNonVoidPropertySetter.class);
        assertEquals("zoobar", bean.getStringField());
    }

    // NOTE: failed with databind-2.5.0; fixed for 2.5.1
    public void testStringBuilder() throws Exception
    {
        StringBuilder sb = MAPPER.readValue(quote("foobar"), StringBuilder.class);
        assertEquals("foobar", sb.toString());
    }

    public void testBooleans() throws Exception
    {
        BooleansBean bean = MAPPER.readValue(aposToQuotes("{'a':true, 'b':true}"),
                BooleansBean.class);
        assertNotNull(bean);
        assertTrue(bean.a);
        assertEquals(Boolean.TRUE, bean._b);
    }

    // for [module-afterburner#60] (caused by a bug in jackson-core up to 2.6.2, fixed in 2.6.3)
    public void testProblemWithIndentation() throws Exception {
        final String JSON = "{\n"
+"            \"foos\" :\n"
+"            [\n"
+"            ]\n"
+"}";

        // First: read from String directly
        
        Issue60Pojo pojo = MAPPER.readValue(JSON, Issue60Pojo.class);
        assertNotNull(pojo);
        assertNotNull(pojo.foos);
        assertEquals(0, pojo.foos.size());

        // and then as bytes via InputStream
        pojo = MAPPER.readValue(JSON.getBytes("UTF-8"), Issue60Pojo.class);
        assertNotNull(pojo);
        assertNotNull(pojo.foos);
        assertEquals(0, pojo.foos.size());
    }

    @SuppressWarnings("unchecked")
    public void testGeneratedDeserializerName() throws Exception {
        MAPPER.readValue("{\"stringField\":\"foo\"}", CheckGeneratedDeserializerName.class);
        ClassLoader cl = getClass().getClassLoader();
        Field declaredField = ClassLoader.class.getDeclaredField("classes");
        declaredField.setAccessible(true);
        // 06-Sep-2017, tatu: Hmmh. Whatever this code does... is not very robust.
        //    But has to do for now. OpenJDK 7 had issues with size, increased:
        Class<?>[] os = new Class[8192];
        ((Vector<Class<?>>) declaredField.get(cl)).copyInto(os);

        String expectedClassName = TestSimpleDeserialize.class.getCanonicalName()
                + "$CheckGeneratedDeserializerName$Access4JacksonDeserializer";
        for (Class<?> clz : os) {
            if (clz == null) {
                break;
            }
            if (clz.getCanonicalName() != null
                    && clz.getCanonicalName().startsWith(expectedClassName)) {
                return;
            }
        }
        fail("Expected class not found:" + expectedClassName);
    }
}
