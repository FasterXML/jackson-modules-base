package com.fasterxml.jackson.module.noctordeser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import junit.framework.TestCase;

import java.util.Arrays;

public class BasicNoConstructorTest extends TestCase
{
    static class BeanWithoutDefaultConstructor {
        public String value;

        public BeanWithoutDefaultConstructor(String value) {
            this.value = value;
        }
    }

    static class BeanWithDefaultConstructor {
        public String value;

        public BeanWithDefaultConstructor() { }
    }

    static class BeanWithoutDefault2 {
        public int x, y;

        public BeanWithoutDefault2(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    protected static ObjectMapper newObjectMapper() {
        return JsonMapper.builder()
                .addModule(new NoCtorModule())
                .build();
    }

    private final ObjectMapper MAPPER = newObjectMapper();

    public void testReadValueWithoutDefaultConstructor() throws Exception
    {
        String json = MAPPER.writeValueAsString(new BeanWithoutDefaultConstructor("test"));

        // First, test default behavior without module (should fail)
        ObjectMapper objectMapper = new JsonMapper();
        try {
            objectMapper.readValue(json, BeanWithoutDefaultConstructor.class);
            fail("should not pass");
        } catch (Exception e) {
            verifyException(e, "Cannot construct instance");
        }
        // And then with module added:
        BeanWithoutDefaultConstructor result = MAPPER.readValue(json,
                BeanWithoutDefaultConstructor.class);
        assertNotNull(result);
        assertEquals("test", result.value);

        // Also test a 2-property one
        json = MAPPER.writeValueAsString(new BeanWithoutDefault2(3, 7));
        BeanWithoutDefault2 result2 =  MAPPER.readValue(json,
                BeanWithoutDefault2.class);
        assertEquals(3, result2.x);
        assertEquals(7, result2.y);
    }

    public void testReadValueWithDefaultConstructor() throws Exception {
        BeanWithDefaultConstructor bean = new BeanWithDefaultConstructor();
        bean.value = "test";
        byte[] bytes = MAPPER.writeValueAsBytes(bean);

        BeanWithDefaultConstructor result = MAPPER.readValue(bytes, BeanWithDefaultConstructor.class);
        assertNotNull(result);
        assertEquals("test", result.value);

        ObjectMapper objectMapper = new ObjectMapper();
        BeanWithDefaultConstructor result2 = objectMapper.readValue(bytes, BeanWithDefaultConstructor.class);
        assertNotNull(result2);
        assertEquals("test", result2.value);
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