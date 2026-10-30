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
    
    // Getter fails when called through the generated accessor, and works when
    // called through reflection (the fallback writer)
    static class MyBean
    {
        private String e;

        int optimizedCalls, reflectiveCalls;

        public MyBean() { }

        MyBean(String e)
        {
            this.e = e;
        }

        public void setE(String e)
        {
            this.e = e;
        }

        public String getE()
        {
            // nearest of `Method.invoke` or a Blackbird frame tells which path called
            for (StackTraceElement elem : new Throwable().getStackTrace()) {
                if (elem.getClassName().equals("java.lang.reflect.Method")) {
                    ++reflectiveCalls;
                    return e;
                }
                if (elem.getClassName().startsWith("com.fasterxml.jackson.module.blackbird.ser.")) {
                    ++optimizedCalls;
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
        MyBean bean = new MyBean("a");
        assertEquals(BEAN_JSON, abMapper.writeValueAsString(bean));
        // the generated getter failed, and the value was then written through reflection
        assertEquals(1, bean.optimizedCalls);
        assertEquals(1, bean.reflectiveCalls);

        // after that, the writer stays on reflection
        MyBean bean2 = new MyBean("a");
        assertEquals(BEAN_JSON, abMapper.writeValueAsString(bean2));
        assertEquals(0, bean2.optimizedCalls);
        assertEquals(1, bean2.reflectiveCalls);
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
