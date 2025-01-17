package tools.jackson.module.blackbird;

//import org.junit.Ignore;
//import org.junit.Test;

//import java.lang.reflect.Proxy;

import tools.jackson.databind.ObjectMapper;

public class TestAccessFallback extends BlackbirdTestBase
{
    @SuppressWarnings("serial")
    public static class BogusTestError extends IllegalAccessError {
        public BogusTestError(String msg) {
            super(msg);
        }
    }
    
    public static class MyBean
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

    public void testSerializeAccess() throws Exception
    {
        ObjectMapper abMapper = newObjectMapper();
        assertEquals(BEAN_JSON, abMapper.writeValueAsString(new MyBean("a")));

        // actually try again, to ensure handling works reliably
        assertEquals(BEAN_JSON, abMapper.writeValueAsString(new MyBean("a")));
    }

    public void testDeserializeAccess() throws Exception
    {
        ObjectMapper abMapper = newObjectMapper();
        MyBean bean = abMapper.readValue(BEAN_JSON, MyBean.class);
        assertEquals("a", bean.getE());

        // actually try again, to ensure handling works reliably
        MyBean bean2 = abMapper.readValue(BEAN_JSON, MyBean.class);
        assertEquals("a", bean2.getE());
    }

    /*
    @Ignore("Does not work on JDK17+, JPMS")
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
    */

    public interface Beany {
        int getA();
    }
}
