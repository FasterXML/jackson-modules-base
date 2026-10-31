package tools.jackson.module.blackbird.inject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Public-field access is generated: a bean whose properties are all public
// fields engages a Blackbird codec that stores through putfield (read side) and
// getfield (write side), the same as a setter/getter POJO. Values must match
// stock databind.
public class FieldAccessTest extends BlackbirdInjectionTestBase
{
    public static class FieldOnlyBean {
        public int intField;
        public long longField;
        public boolean boolField;
        public String stringField;
    }

    private final Harness h = newHarness();

    @Test
    public void testFieldBackedBeanEngagesCodec() throws Exception
    {
        FieldOnlyBean bean = h.mapper.readValue(
                "{\"intField\":1,\"longField\":2,\"boolField\":true,\"stringField\":\"x\"}",
                FieldOnlyBean.class);
        assertEquals(1, bean.intField);
        assertEquals(2L, bean.longField);
        assertTrue(bean.boolField);
        assertEquals("x", bean.stringField);

        assertTrue(isBlackbirdReader(h.deserFor(FieldOnlyBean.class)),
                "FieldOnlyBean did not engage a Blackbird codec: "
                        + h.deserFor(FieldOnlyBean.class).getClass().getName());
    }
}
