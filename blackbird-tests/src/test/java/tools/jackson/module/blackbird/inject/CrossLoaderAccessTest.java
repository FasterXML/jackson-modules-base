package tools.jackson.module.blackbird.inject;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.deser.SettableBeanProperty;

import static org.junit.jupiter.api.Assertions.*;

// Documents a finding about Blackbird's CrossLoaderAccess code path:
// on JDK 9+ with a bean in the unnamed module (classpath), the companion
// `$$JacksonBlackbirdAccess` class is **never** defined.
//
// Why: BBDeserializerModifier.updateBuilder obtains a lookup via
// MethodHandles.lookup() inside BlackbirdModule (full privilege on blackbird's
// own class), then calls ReflectionHack.privateLookupIn(beanClass, lookup).
// For an unnamed-module bean, privateLookupIn always succeeds and returns a
// lookup with hasFullPrivilegeAccess() == true over the bean's package. Then
// CrossLoaderAccess.grantAccess short-circuits on `hasFullAccess(lookup)` and
// returns the lookup unchanged, skipping the slow path that would define a
// $$JacksonBlackbirdAccess companion class via Lookup.defineClass(byte[]).
//
// That slow path exists for historical / edge-case scenarios (JDK 8 when
// DEFINE_CLASS is null, or a future JDK change that lowers privileges on
// privateLookupIn). It is not exercised by either Blackbird's in-tree test
// suite (same-module case is also full-privilege) nor by this classpath
// test module.
//
// This test pins the current behavior: the fast path must win, and the
// companion class must not be introduced as a side effect of deserialization.
// If Blackbird's CrossLoaderAccess logic changes such that the slow path
// starts firing for classpath POJOs, this test will catch it — at which
// point the maintainer should decide whether that's intended (and update the
// assertion) or accidental (and revert).
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

        // Optimization still ran — setters are Blackbird-optimized.
        SettableBeanProperty[] props = propsOf(h.deserFor(XLoaderBean.class));
        assertEquals(2, props.length);
        for (SettableBeanProperty p : props) {
            assertTrue(isOptimizedProperty(p),
                    "XLoaderBean." + p.getName() + " not optimized: "
                            + p.getClass().getName());
        }

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
