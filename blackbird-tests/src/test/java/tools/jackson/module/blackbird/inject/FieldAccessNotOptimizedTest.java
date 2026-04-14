package tools.jackson.module.blackbird.inject;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.deser.SettableBeanProperty;

import static org.junit.jupiter.api.Assertions.*;

// Documents a known design limitation of Blackbird: direct public-field access
// is NOT optimized. BBDeserializerModifier.nextProperty only handles properties
// whose backing JDK member is a Method (setter); if it's a Field, the method
// returns early and the property is left as a plain FieldProperty. Afterburner
// optimizes both setter and field access; Blackbird deliberately doesn't.
//
// The point of this test is to pin that contract. If Blackbird ever grows
// field-access support, this test will start failing — which is the correct
// signal to update it.
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
    public void testFieldPropsAreNotReplacedWithOptimizedVersions() throws Exception
    {
        // End-to-end deserialization must still work — Blackbird just delegates
        // to databind's plain FieldProperty for these.
        FieldOnlyBean bean = h.mapper.readValue(
                "{\"intField\":1,\"longField\":2,\"boolField\":true,\"stringField\":\"x\"}",
                FieldOnlyBean.class);
        assertEquals(1, bean.intField);
        assertEquals(2L, bean.longField);
        assertTrue(bean.boolField);
        assertEquals("x", bean.stringField);

        // None of the properties should be Blackbird-optimized.
        SettableBeanProperty[] props = propsOf(h.deserFor(FieldOnlyBean.class));
        assertEquals(4, props.length);
        for (SettableBeanProperty p : props) {
            assertFalse(isOptimizedProperty(p),
                    "Blackbird unexpectedly optimized field property '" + p.getName()
                            + "' (is " + p.getClass().getName() + "). If Blackbird"
                            + " has grown field-access support, update this test to"
                            + " assert the positive case instead.");
        }
    }
}
