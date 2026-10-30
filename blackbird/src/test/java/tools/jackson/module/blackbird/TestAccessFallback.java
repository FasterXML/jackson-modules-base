package tools.jackson.module.blackbird;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestAccessFallback extends BlackbirdTestBase
{
    @SuppressWarnings("serial")
    public static class BogusTestError extends IllegalAccessError {
        public BogusTestError(String msg) {
            super(msg);
        }
    }
    
    // Getter fails when called through the generated accessor, and works when
    // called by the fallback writer
    public static class MyBean
    {
        private String e;

        int optimizedCalls, fallbackCalls;

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
            // nearest of databind's `BeanPropertyWriter` (which calls the getter through
            // a method handle) or a Blackbird frame tells which path called
            for (StackTraceElement elem : new Throwable().getStackTrace()) {
                if (elem.getClassName().equals("tools.jackson.databind.ser.BeanPropertyWriter")) {
                    ++fallbackCalls;
                    return e;
                }
                if (elem.getClassName().startsWith("tools.jackson.module.blackbird.ser.")) {
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
        // the generated getter failed, and the value was then written by the fallback writer
        assertEquals(1, bean.optimizedCalls);
        assertEquals(1, bean.fallbackCalls);

        // after that, the fallback writer is used directly
        MyBean bean2 = new MyBean("a");
        assertEquals(BEAN_JSON, abMapper.writeValueAsString(bean2));
        assertEquals(0, bean2.optimizedCalls);
        assertEquals(1, bean2.fallbackCalls);
    }

    // 18-Jan-2025, tatu: Fails on JDK-17/JPMS, for reasons unknown.
    //    need to comment out.
    /*
    @Test
    public void testProxyAccessIssue181() throws Exception {
        ObjectMapper om = newObjectMapper();
        String val = om.writeValueAsString(Proxy.newProxyInstance(TestAccessFallback.class.getClassLoader(),
          new Class<?>[] { Beany.class }, (p, m, a) -> {
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
    */
}
