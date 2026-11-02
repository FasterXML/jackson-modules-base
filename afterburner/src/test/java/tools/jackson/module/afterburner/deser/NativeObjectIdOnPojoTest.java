package tools.jackson.module.afterburner.deser;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.util.TokenBuffer;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

// A native Object Id in input (such as a YAML anchor) for a bean that does not use
// Object Ids has no meaning: fast paths must ignore it, as stock fast path does.
public class NativeObjectIdOnPojoTest extends AfterburnerTestBase
{
    static class NarrowBean {
        public String a, b;
    }

    static class WideBean {
        public String a, b, c, d, e, f, g, h;
    }

    private final static String[] NAMES = { "a", "b", "c", "d", "e", "f", "g", "h" };

    @Test
    public void testStockIgnoresId() throws Exception
    {
        _verifyNarrow(newVanillaJSONMapper());
        _verifyWide(newVanillaJSONMapper());
    }

    @Test
    public void testNarrowBeanIgnoresId() throws Exception
    {
        _verifyNarrow(newAfterburnerMapper());
    }

    @Test
    public void testWideBeanIgnoresId() throws Exception
    {
        _verifyWide(newAfterburnerMapper());
    }

    private void _verifyNarrow(ObjectMapper mapper) throws Exception
    {
        try (TokenBuffer buf = _withObjectId(2);
                JsonParser p = buf.asParser(ObjectReadContext.empty())) {
            NarrowBean bean = mapper.readerFor(NarrowBean.class).readValue(p);
            assertEquals("a", bean.a);
            assertEquals("b", bean.b);
        }
    }

    private void _verifyWide(ObjectMapper mapper) throws Exception
    {
        try (TokenBuffer buf = _withObjectId(NAMES.length);
                JsonParser p = buf.asParser(ObjectReadContext.empty())) {
            WideBean bean = mapper.readerFor(WideBean.class).readValue(p);
            assertEquals("a", bean.a);
            assertEquals("h", bean.h);
        }
    }

    private TokenBuffer _withObjectId(int count)
    {
        TokenBuffer buf = new TokenBuffer(true);
        buf.writeObjectId("anchor1");
        buf.writeStartObject();
        for (int i = 0; i < count; ++i) {
            buf.writeName(NAMES[i]);
            buf.writeString(NAMES[i]);
        }
        buf.writeEndObject();
        return buf;
    }
}
