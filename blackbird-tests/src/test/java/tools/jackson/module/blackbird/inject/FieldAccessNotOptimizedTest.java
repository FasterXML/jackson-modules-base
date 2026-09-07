package tools.jackson.module.blackbird.inject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Documents how Blackbird treats direct public-field access: fields are not
// read or written by generated code. The bean still engages a generated codec,
// and each field-backed property routes through databind's stock
// SettableBeanProperty from inside that codec (Kind.STOCK in BBCodecFactory).
// Behavior therefore matches stock databind exactly; only setter-backed
// properties get generated accessor code.
public class FieldAccessNotOptimizedTest extends BlackbirdInjectionTestBase
{
    public static class FieldOnlyBean {
        public int intField;
        public long longField;
        public boolean boolField;
        public String stringField;
    }

    private final Harness h = newHarness();

    @Test
    public void testFieldBackedBeanDelegatesToStockProperties() throws Exception
    {
        FieldOnlyBean bean = h.mapper.readValue(
                "{\"intField\":1,\"longField\":2,\"boolField\":true,\"stringField\":\"x\"}",
                FieldOnlyBean.class);
        assertEquals(1, bean.intField);
        assertEquals(2L, bean.longField);
        assertTrue(bean.boolField);
        assertEquals("x", bean.stringField);

        // The codec engages for the bean even though every property is
        // field-backed; the fields ride the codec's stock-property arms.
        assertTrue(isBlackbirdDeserCodec(h.deserFor(FieldOnlyBean.class)),
                "FieldOnlyBean did not engage a Blackbird codec: "
                        + h.deserFor(FieldOnlyBean.class).getClass().getName());
    }
}
