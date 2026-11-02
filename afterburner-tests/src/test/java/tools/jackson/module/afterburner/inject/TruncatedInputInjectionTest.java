package tools.jackson.module.afterburner.inject;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.util.TokenBuffer;

import static org.junit.jupiter.api.Assertions.*;

// Verifies that token stream ending right after a property name is reported, instead of
// giving a partly populated bean, by generated mutators of each specialized type: the
// in-tree Afterburner tests run in a named module, where bytecode injection is disabled.
public class TruncatedInputInjectionTest extends AfterburnerInjectionTestBase
{
    public static class StringBean {
        public String value;
    }
    public static class IntBean {
        public int value;
    }
    public static class LongBean {
        public long value;
    }
    public static class BooleanBean {
        public boolean value;
    }

    private final Harness h = newHarness();

    @Test
    public void testStringMutator() throws Exception {
        _verifyFails(StringBean.class, new StringBean());
    }

    @Test
    public void testIntMutator() throws Exception {
        _verifyFails(IntBean.class, new IntBean());
    }

    @Test
    public void testLongMutator() throws Exception {
        _verifyFails(LongBean.class, new LongBean());
    }

    @Test
    public void testBooleanMutator() throws Exception {
        _verifyFails(BooleanBean.class, new BooleanBean());
    }

    private void _verifyFails(Class<?> beanClass, Object toUpdate) throws Exception
    {
        ObjectMapper mapper = h.mapper;
        mapper._deserializationContext().findRootValueDeserializer(mapper.constructType(beanClass));
        ValueDeserializer<?> deser = h.deserFor(beanClass);
        for (SettableBeanProperty prop : propsOf(deser)) {
            assertTrue(isOptimizedProperty(prop),
                    "Property '"+prop.getName()+"' not optimized: "+prop.getClass().getName());
        }
        try (TokenBuffer buf = _truncatedAfterName();
                JsonParser p = buf.asParser(ObjectReadContext.empty())) {
            assertThrows(DatabindException.class, () -> mapper.readerFor(beanClass).readValue(p));
        }
        try (TokenBuffer buf = _truncatedAfterName();
                JsonParser p = buf.asParser(ObjectReadContext.empty())) {
            assertThrows(DatabindException.class, () -> mapper.readerForUpdating(toUpdate).readValue(p));
        }
    }

    private TokenBuffer _truncatedAfterName()
    {
        TokenBuffer buf = TokenBuffer.forGeneration();
        buf.writeStartObject();
        buf.writeName("value");
        return buf;
    }
}
