package com.fasterxml.jackson.module.blackbird;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

public class TestAccessFallback extends BlackbirdTestBase
{
    @SuppressWarnings("serial")
    static class BogusTestError extends IllegalAccessError {
        public BogusTestError(String msg) {
            super(msg);
        }
    }
    
    static class MyBean
    {
        private String e;

        public MyBean() { }

        MyBean(String e)
        {
            setE(e);
        }

        public void setE(String e)
        {
            /* 07-Mar-2015, tatu: This is bit tricky, as the exact stack trace varies
             *   depending on how code is generated. So right now we must get the call
             *   immediately from constructed class.
             */
            
            StackTraceElement[] elems = new Throwable().getStackTrace();
            StackTraceElement prev = elems[1];
            if (prev.getClassName().contains("Access4JacksonDeserializer")) {
                throw new BogusTestError("boom!");
            }
            this.e = e;
        }

        public String getE()
        {
            for (StackTraceElement elem : new Throwable().getStackTrace()) {
                if (elem.getClassName().contains("Access4JacksonSerializer")) {
                    throw new BogusTestError("boom!");
                }
            }
            return e;
        }
    }

    private static final String BEAN_JSON = "{\"e\":\"a\"}";

    @Test
    public void testSerializeAccess() throws Exception
    {
        ObjectMapper abMapper = newObjectMapper();
        assertEquals(BEAN_JSON, abMapper.writeValueAsString(new MyBean("a")));

        // actually try again, to ensure handling works reliably
        assertEquals(BEAN_JSON, abMapper.writeValueAsString(new MyBean("a")));
    }

    @Test
    public void testDeserializeAccess() throws Exception
    {
        ObjectMapper abMapper = newObjectMapper();
        MyBean bean = abMapper.readValue(BEAN_JSON, MyBean.class);
        assertEquals("a", bean.getE());

        // actually try again, to ensure handling works reliably
        MyBean bean2 = abMapper.readValue(BEAN_JSON, MyBean.class);
        assertEquals("a", bean2.getE());
    }

    @Test
    public void testProxyAccessIssue181() throws Exception {
        ObjectMapper om = newObjectMapper();
        String val = om.writeValueAsString(Proxy.newProxyInstance(TestAccessFallback.class.getClassLoader(), new Class<?>[] { Beany.class }, (p, m, a) -> {
            if (m.getName().equals("getA")) {
                return 42;
            }
            return null;
        }));
        assertEquals("{\"a\":42}", val);
    }

    public interface Beany {
        int getA();
    }
}
