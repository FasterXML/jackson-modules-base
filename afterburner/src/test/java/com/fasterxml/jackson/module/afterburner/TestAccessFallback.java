package com.fasterxml.jackson.module.afterburner;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestAccessFallback extends AfterburnerTestBase
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

    public void testSerializeAccess() throws Exception
    {
        ObjectMapper abMapper = newAfterburnerMapper();
        assertEquals(BEAN_JSON, abMapper.writeValueAsString(new MyBean("a")));

        // actually try again, to ensure handling works reliably
        assertEquals(BEAN_JSON, abMapper.writeValueAsString(new MyBean("a")));
    }

    public void testDeserializeAccess() throws Exception
    {
        ObjectMapper abMapper = newAfterburnerMapper();
        MyBean bean = abMapper.readValue(BEAN_JSON, MyBean.class);
        assertEquals("a", bean.getE());

        // actually try again, to ensure handling works reliably
        MyBean bean2 = abMapper.readValue(BEAN_JSON, MyBean.class);
        assertEquals("a", bean2.getE());
    }
}
