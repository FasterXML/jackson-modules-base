package tools.jackson.module.blackbird.inject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Pins the CrossLoaderAccess fast path for classpath POJOs: on JDK 9+ with a
// bean in the unnamed module, privateLookupIn returns a full-privilege lookup,
// CrossLoaderAccess.grantAccess short-circuits, and the companion
// `$$JacksonBlackbirdAccess` class is never defined. The slow path exists only
// for historical edge cases and no in-tree suite exercises it.
//
// CrossLoaderAccess is deprecated for removal (modules-base#350); this test is
// deleted together with it.
public class CrossLoaderAccessTest extends BlackbirdInjectionTestBase
{
    public static class XLoaderBean {
        private int a;
        private String b;
        public int getA() { return a; }
        public void setA(int a) { this.a = a; }
        public String getB() { return b; }
        public void setB(String b) { this.b = b; }
    }

    private static final String COMPANION_CLASS_NAME =
            XLoaderBean.class.getPackage().getName() + ".$$JacksonBlackbirdAccess";

    @Test
    public void testFastPathWins_NoCompanionClassDefined() throws Exception
    {
        Harness h = newHarness();

        // Trigger Blackbird's modifier chain for XLoaderBean.
        XLoaderBean bean = h.mapper.readValue("{\"a\":1,\"b\":\"hi\"}", XLoaderBean.class);
        assertEquals(1, bean.getA());
        assertEquals("hi", bean.getB());

        // The codec engaged for the unnamed-module bean.
        assertTrue(isBlackbirdDeserCodec(h.deserFor(XLoaderBean.class)),
                "XLoaderBean did not engage a Blackbird codec: "
                        + h.deserFor(XLoaderBean.class).getClass().getName());

        // But the $$JacksonBlackbirdAccess companion class must NOT have been
        // defined — CrossLoaderAccess.grantAccess short-circuits for a lookup
        // with hasFullPrivilegeAccess() == true, which is what privateLookupIn
        // returns for an unnamed-module target.
        assertFalse(companionClassExists(),
                "CrossLoaderAccess unexpectedly defined " + COMPANION_CLASS_NAME
                        + " for an unnamed-module bean — the grantAccess fast path"
                        + " should have short-circuited. If Blackbird has started"
                        + " taking the slow path (e.g. because privateLookupIn"
                        + " semantics changed), update this test.");
    }

    private static boolean companionClassExists() {
        try {
            Class.forName(COMPANION_CLASS_NAME, false,
                    XLoaderBean.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
