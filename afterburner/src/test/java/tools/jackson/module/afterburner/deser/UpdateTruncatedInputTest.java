package tools.jackson.module.afterburner.deser;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.util.TokenBuffer;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

// Update fast paths of SuperSonic deserializers must treat token streams that end
// before END_OBJECT as stock does ([databind#6357]), not return partly updated
// bean; and still handle input that does not match the expected property order.
public class UpdateTruncatedInputTest extends AfterburnerTestBase
{
    static class NarrowBean {
        public String a, b;
    }

    static class WideBean {
        public String a, b, c, d, e, f, g, h;
    }

    private final static String[] NAMES = { "a", "b", "c", "d", "e", "f", "g", "h" };

    private final ObjectMapper MAPPER = newAfterburnerMapper();

    @Test
    public void testNarrowBeanTruncated() throws Exception
    {
        for (int count = 0; count <= 2; ++count) {
            _verifyTruncated(new NarrowBean(), count);
        }
    }

    @Test
    public void testWideBeanTruncated() throws Exception
    {
        for (int count = 0; count <= NAMES.length; ++count) {
            _verifyTruncated(new WideBean(), count);
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

    private void _verifyTruncated(Object bean, int count) throws Exception
    {
        try (TokenBuffer buf = TokenBuffer.forGeneration()) {
            buf.writeStartObject();
            for (int i = 0; i < count; ++i) {
                buf.writeName(NAMES[i]);
                buf.writeString("x");
            }
            try (JsonParser p = buf.asParser(ObjectReadContext.empty())) {
                assertThrows(MismatchedInputException.class,
                        () -> MAPPER.readerForUpdating(bean).readValue(p),
                        bean.getClass().getSimpleName()+" with "+count+" properties");
            }
        }
    }
}
