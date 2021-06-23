package com.fasterxml.jackson.module.enhance.deser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import junit.framework.TestCase;

import java.util.Arrays;

public class EnhanceDeserTestBase extends TestCase {

    static class BeanWithoutDefaultConstructor {
        private String value;

        public BeanWithoutDefaultConstructor(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    static class BeanWithDefaultConstructor {
        private String value;

        public BeanWithDefaultConstructor() {
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    protected static ObjectMapper newObjectMapper() {
        return JsonMapper.builder()
                .addModule(new EnhanceDeserModule())
                .build();
    }

    private final ObjectMapper MAPPER = newObjectMapper();

    public void testReadValueWithoutDefaultConstructor() throws Exception {
        BeanWithoutDefaultConstructor bean = new BeanWithoutDefaultConstructor("test");
        byte[] bytes = MAPPER.writeValueAsBytes(bean);

        BeanWithoutDefaultConstructor result = MAPPER.readValue(bytes, BeanWithoutDefaultConstructor.class);
        assertNotNull(result);
        assertEquals("test", result.getValue());

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.readValue(bytes, BeanWithoutDefaultConstructor.class);
            fail("should not pass");
        } catch (Exception e) {
            verifyException(e, "Cannot construct instance");
        }
    }

    public void testReadValueWithDefaultConstructor() throws Exception {
        BeanWithDefaultConstructor bean = new BeanWithDefaultConstructor();
        bean.setValue("test");
        byte[] bytes = MAPPER.writeValueAsBytes(bean);

        BeanWithDefaultConstructor result = MAPPER.readValue(bytes, BeanWithDefaultConstructor.class);
        assertNotNull(result);
        assertEquals("test", result.getValue());

        ObjectMapper objectMapper = new ObjectMapper();
        BeanWithDefaultConstructor result2 = objectMapper.readValue(bytes, BeanWithDefaultConstructor.class);
        assertNotNull(result2);
        assertEquals("test", result2.getValue());
    }

    public static void verifyException(Throwable e, String... matches) {
        String msg = e.getMessage();
        String lmsg = (msg == null) ? "" : msg.toLowerCase();
        for (String match : matches) {
            String lmatch = match.toLowerCase();
            if (lmsg.contains(lmatch)) {
                return;
            }
        }
        fail("Expected an exception with one of substrings ("
                + Arrays.asList(matches) + "): got one (of type " + e.getClass().getName()
                + ") with message \"" + msg + "\"");
    }
}