package tools.jackson.module.afterburner.inject;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.deser.ValueInstantiator;
import tools.jackson.databind.deser.bean.BeanDeserializer;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

// Verifies CreatorOptimizer kicks in for a POJO that uses the default constructor:
// the deserializer's ValueInstantiator should be replaced with an Afterburner-generated
// subclass of StdValueInstantiator, not the plain StdValueInstantiator itself. If
// CreatorOptimizer's ByteBuddy codegen or its "install on the builder" wiring breaks,
// this test surfaces it.
public class CreatorOptimizerTest extends AfterburnerInjectionTestBase
{
    public static class DefaultCtorBean {
        public int a;
        public String b;
    }

    @Test
    public void testCreatorReplacedForDefaultCtor() throws Exception
    {
        Harness h = newHarness();
        DefaultCtorBean bean = h.mapper.readValue("{\"a\":1,\"b\":\"hi\"}", DefaultCtorBean.class);
        assertEquals(1, bean.a);
        assertEquals("hi", bean.b);

        BeanDeserializer bd = (BeanDeserializer) h.deserFor(DefaultCtorBean.class);

        // Read _valueInstantiator from BeanDeserializerBase via reflection.
        ValueInstantiator inst = readValueInstantiator(bd);
        assertNotNull(inst);

        // Afterburner replaces the plain StdValueInstantiator with a bytecode-generated
        // subclass whose class-chain includes OptimizedValueInstantiator — the abstract
        // base CreatorOptimizer produces subclasses of. Checking for that base in the
        // chain is sufficient: it can only appear if CreatorOptimizer actually ran.
        assertTrue(classChainIncludes(inst.getClass(), "OptimizedValueInstantiator"),
                "CreatorOptimizer did not replace the ValueInstantiator; got "
                        + inst.getClass().getName());
    }

    private static boolean classChainIncludes(Class<?> cls, String simpleName) {
        Class<?> c = cls;
        while (c != null) {
            if (simpleName.equals(c.getSimpleName())) return true;
            c = c.getSuperclass();
        }
        return false;
    }

    private static ValueInstantiator readValueInstantiator(BeanDeserializer bd) throws Exception {
        Class<?> c = bd.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField("_valueInstantiator");
                f.setAccessible(true);
                return (ValueInstantiator) f.get(bd);
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            }
        }
        throw new AssertionError("_valueInstantiator not found");
    }
}
