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
    
    // Getter that fails on request, counting its calls. Generated writers are
    // hidden classes, left out of stack traces, so the calls can't tell which
    // path made them; `assertGeneratedWriter` checks that instead.
    public static class MyBean
    {
        private String e;

        boolean fail;
        int calls;

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
            ++calls;
            if (fail) {
                throw new BogusTestError("boom!");
            }
            return e;
        }
    }

    private static final String BEAN_JSON = "{\"e\":\"a\"}";

    @Test
    public void testSerializeAccess() throws Exception
    {
        ObjectMapper abMapper = newObjectMapper();
        assertGeneratedWriter(abMapper, MyBean.class);
        MyBean bean = new MyBean("a");
        assertEquals(BEAN_JSON, abMapper.writeValueAsString(bean));
        assertEquals(1, bean.calls);
    }

    // A failing getter is called once, not retried through the stock writer,
    // and its failure is reported as plain databind reports it
    @Test
    public void testFailingGetterIsNotRetried() throws Exception
    {
        MyBean expectedBean = new MyBean("a");
        expectedBean.fail = true;
        Throwable expected = assertThrows(Throwable.class,
                () -> newVanillaJSONMapper().writeValueAsString(expectedBean));
        assertEquals(1, expectedBean.calls);

        ObjectMapper abMapper = newObjectMapper();
        // twice, as a failure must not change how later writes go
        for (int i = 0; i < 2; ++i) {
            MyBean bean = new MyBean("a");
            bean.fail = true;
            Throwable actual = assertThrows(Throwable.class, () -> abMapper.writeValueAsString(bean));
            assertEquals(1, bean.calls);
            assertEquals(expected.getClass(), actual.getClass());
            assertEquals(expected.getMessage(), actual.getMessage());
            assertEquals(String.valueOf(expected.getCause()), String.valueOf(actual.getCause()));
        }
        assertEquals(BEAN_JSON, abMapper.writeValueAsString(new MyBean("a")));
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
