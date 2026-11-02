package tools.jackson.module.afterburner.deser;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.util.TokenBuffer;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

// Fast paths of SuperSonic deserializers must treat token streams that end before
// END_OBJECT as stock does ([databind#6357]), not return a partly populated bean;
// and still handle input that does not match the expected property order.
public class TruncatedInputTest extends AfterburnerTestBase
{
    static class NarrowBean {
        public String a, b;
    }

    static class WideBean {
        public String a, b, c, d, e, f, g, h;
    }

    private final static String[] NAMES = { "a", "b", "c", "d", "e", "f", "g", "h" };

    private final ObjectMapper MAPPER = newAfterburnerMapper();

    private final ObjectMapper STOCK_MAPPER = newVanillaJSONMapper();

    /*
    /**********************************************************************
    /* Truncated after complete properties
    /**********************************************************************
     */

    @Test
    public void testNarrowBeanUpdateTruncated() throws Exception
    {
        for (int count = 0; count <= 2; ++count) {
            _verifyUpdateFails(new NarrowBean(), count, false);
        }
    }

    @Test
    public void testWideBeanUpdateTruncated() throws Exception
    {
        for (int count = 0; count <= NAMES.length; ++count) {
            _verifyUpdateFails(new WideBean(), count, false);
        }
    }

    @Test
    public void testNarrowBeanReadTruncated() throws Exception
    {
        for (int count = 0; count <= 2; ++count) {
            _verifyReadFails(NarrowBean.class, count, false);
        }
    }

    @Test
    public void testWideBeanReadTruncated() throws Exception
    {
        for (int count = 0; count <= NAMES.length; ++count) {
            _verifyReadFails(WideBean.class, count, false);
        }
    }

    /*
    /**********************************************************************
    /* Truncated right after property name
    /**********************************************************************
     */

    @Test
    public void testNarrowBeanTruncatedAfterName() throws Exception
    {
        for (int count = 0; count < 2; ++count) {
            _verifyUpdateFails(new NarrowBean(), count, true);
            _verifyReadFails(NarrowBean.class, count, true);
        }
    }

    @Test
    public void testWideBeanTruncatedAfterName() throws Exception
    {
        for (int count = 0; count < NAMES.length; ++count) {
            _verifyUpdateFails(new WideBean(), count, true);
            _verifyReadFails(WideBean.class, count, true);
        }
    }

    /*
    /**********************************************************************
    /* Complete but partial, disordered input
    /**********************************************************************
     */

    @Test
    public void testNarrowBeanPartial() throws Exception
    {
        NarrowBean bean = new NarrowBean();
        bean.b = "old";
        assertSame(bean, MAPPER.readerForUpdating(bean).readValue(a2q("{'a':'1'}")));
        assertEquals("1", bean.a);
        assertEquals("old", bean.b);

        bean = MAPPER.readValue(a2q("{'a':'1'}"), NarrowBean.class);
        assertEquals("1", bean.a);
        assertNull(bean.b);
    }

    @Test
    public void testWideBeanPartial() throws Exception
    {
        // Ends both within the unrolled properties, and within the loop
        for (String json : new String[] {
                "{'a':'1'}",
                "{'a':'1','b':'2','c':'3','d':'4','e':'5'}"
        }) {
            WideBean bean = new WideBean();
            bean.h = "old";
            assertSame(bean, MAPPER.readerForUpdating(bean).readValue(a2q(json)));
            assertEquals("1", bean.a);
            assertEquals("old", bean.h);

            bean = MAPPER.readValue(a2q(json), WideBean.class);
            assertEquals("1", bean.a);
            assertNull(bean.h);
        }
    }

    @Test
    public void testNarrowBeanDisordered() throws Exception
    {
        NarrowBean bean = new NarrowBean();
        assertSame(bean, MAPPER.readerForUpdating(bean)
                .readValue(a2q("{'b':'2','a':'1'}")));
        assertEquals("1", bean.a);
        assertEquals("2", bean.b);
    }

    @Test
    public void testWideBeanDisorderedInLoop() throws Exception
    {
        // Past the unrolled properties, so that miss occurs within the loop
        WideBean bean = new WideBean();
        assertSame(bean, MAPPER.readerForUpdating(bean)
                .readValue(a2q("{'a':'1','b':'2','c':'3','d':'4','e':'5','h':'8','g':'7','f':'6'}")));
        _verifyWide(bean, "1");
    }

    @Test
    public void testWideBeanExtraProperties() throws Exception
    {
        // All properties in expected order, followed by a known and an unknown one
        WideBean bean = new WideBean();
        assertSame(bean, MAPPER.readerForUpdating(bean)
                .readValue(a2q("{'a':'1','b':'2','c':'3','d':'4','e':'5','f':'6','g':'7','h':'8',"
                        +"'a':'again','unknown':true}")));
        _verifyWide(bean, "again");
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    private void _verifyWide(WideBean bean, String expA)
    {
        assertEquals(expA, bean.a);
        assertEquals("2", bean.b);
        assertEquals("3", bean.c);
        assertEquals("4", bean.d);
        assertEquals("5", bean.e);
        assertEquals("6", bean.f);
        assertEquals("7", bean.g);
        assertEquals("8", bean.h);
    }

    private void _verifyUpdateFails(Object bean, int count, boolean danglingName) throws Exception
    {
        String desc = "update of "+bean.getClass().getSimpleName()+", "+count+" properties"
                +(danglingName ? " and name" : "");
        for (ObjectMapper mapper : new ObjectMapper[] { STOCK_MAPPER, MAPPER }) {
            try (TokenBuffer buf = _truncated(count, danglingName);
                    JsonParser p = buf.asParser(ObjectReadContext.empty())) {
                assertThrows(_expectedFailure(danglingName),
                        () -> mapper.readerForUpdating(bean).readValue(p), desc);
            }
        }
    }

    private void _verifyReadFails(Class<?> type, int count, boolean danglingName) throws Exception
    {
        String desc = "read of "+type.getSimpleName()+", "+count+" properties"
                +(danglingName ? " and name" : "");
        for (ObjectMapper mapper : new ObjectMapper[] { STOCK_MAPPER, MAPPER }) {
            try (TokenBuffer buf = _truncated(count, danglingName);
                    JsonParser p = buf.asParser(ObjectReadContext.empty())) {
                assertThrows(_expectedFailure(danglingName),
                        () -> mapper.readerFor(type).readValue(p), desc);
            }
        }
    }

    // With a dangling name, value deserializer sees no token at all: for now, stock
    // only fails by wrapping the resulting NPE, so cannot expect more specific type
    private Class<? extends DatabindException> _expectedFailure(boolean danglingName)
    {
        return danglingName ? DatabindException.class : MismatchedInputException.class;
    }

    private TokenBuffer _truncated(int count, boolean danglingName)
    {
        TokenBuffer buf = TokenBuffer.forGeneration();
        buf.writeStartObject();
        for (int i = 0; i < count; ++i) {
            buf.writeName(NAMES[i]);
            buf.writeString("x");
        }
        if (danglingName) {
            buf.writeName(NAMES[count]);
        }
        return buf;
    }
}
