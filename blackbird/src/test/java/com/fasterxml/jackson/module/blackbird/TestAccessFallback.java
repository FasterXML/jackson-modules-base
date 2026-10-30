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
