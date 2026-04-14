package tools.jackson.module.afterburner.inject;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.deser.ValueInstantiator;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.deser.std.StdValueInstantiator;

import static org.junit.jupiter.api.Assertions.*;

// Covers CreatorOptimizer's main branches:
//
//  1. Default public constructor  -> creator replaced with generated subclass.
//  2. Public static factory method -> creator replaced with generated subclass
//     (the same generated-bytecode path, different stack manipulation).
//  3. Property-based @JsonCreator  -> CreatorOptimizer bails out
//     (canCreateFromObjectWith() is true); the plain StdValueInstantiator stays.
//
// Together these guard against (a) CreatorOptimizer silently breaking for one
// of its two supported paths, and (b) CreatorOptimizer accidentally running on
// an unsupported shape (which would turn into a deserialization failure at
// runtime rather than a clean fallback).
public class CreatorOptimizerTest extends AfterburnerInjectionTestBase
{
    public static class DefaultCtorBean {
        public int a;
        public String b;
    }

    public static class FactoryMethodBean {
        public int a;
        public String b;

        // Private default ctor forces databind to pick up the static factory.
        // The @JsonCreator annotation with no args is how databind recognizes a
        // no-arg static method as a "default creator" — without the annotation
        // databind would fall through to looking for a default constructor.
        private FactoryMethodBean() { }

        @JsonCreator
        public static FactoryMethodBean create() {
            return new FactoryMethodBean();
        }
    }

    public static class PropertyCreatorBean {
        public final int a;
        public final String b;

        @JsonCreator
        public PropertyCreatorBean(@JsonProperty("a") int a, @JsonProperty("b") String b) {
            this.a = a;
            this.b = b;
        }
    }

    private final Harness h = newHarness();

    @Test
    public void testCreatorReplacedForDefaultCtor() throws Exception
    {
        DefaultCtorBean bean = h.mapper.readValue("{\"a\":1,\"b\":\"hi\"}", DefaultCtorBean.class);
        assertEquals(1, bean.a);
        assertEquals("hi", bean.b);

        ValueInstantiator inst = instantiatorFor(DefaultCtorBean.class);
        // OptimizedValueInstantiator is the abstract base CreatorOptimizer produces
        // subclasses of — it can only appear in the chain if CreatorOptimizer ran.
        assertTrue(classChainIncludes(inst.getClass(), "OptimizedValueInstantiator"),
                "CreatorOptimizer did not replace the ValueInstantiator; got "
                        + inst.getClass().getName());
    }

    @Test
    public void testCreatorReplacedForStaticFactory() throws Exception
    {
        FactoryMethodBean bean = h.mapper.readValue("{\"a\":1,\"b\":\"hi\"}",
                FactoryMethodBean.class);
        assertEquals(1, bean.a);
        assertEquals("hi", bean.b);

        ValueInstantiator inst = instantiatorFor(FactoryMethodBean.class);
        assertTrue(classChainIncludes(inst.getClass(), "OptimizedValueInstantiator"),
                "CreatorOptimizer did not replace the ValueInstantiator for a static"
                        + " factory creator; got " + inst.getClass().getName());
    }

    @Test
    public void testCreatorNotReplacedForPropertyBasedCreator() throws Exception
    {
        // Deserialization must still work end-to-end, just without CreatorOptimizer
        // wrapping (it bails out because canCreateFromObjectWith() is true).
        PropertyCreatorBean bean = h.mapper.readValue("{\"a\":1,\"b\":\"hi\"}",
                PropertyCreatorBean.class);
        assertEquals(1, bean.a);
        assertEquals("hi", bean.b);

        ValueInstantiator inst = instantiatorFor(PropertyCreatorBean.class);
        assertFalse(classChainIncludes(inst.getClass(), "OptimizedValueInstantiator"),
                "CreatorOptimizer should NOT wrap a property-based @JsonCreator POJO,"
                        + " but the generated class " + inst.getClass().getName()
                        + " extends OptimizedValueInstantiator");
        assertEquals(StdValueInstantiator.class, inst.getClass(),
                "fallback should be the plain StdValueInstantiator; got "
                        + inst.getClass().getName());
    }

    private ValueInstantiator instantiatorFor(Class<?> cls) {
        BeanDeserializer bd = (BeanDeserializer) h.deserFor(cls);
        ValueInstantiator inst = (ValueInstantiator) reflectField(bd, "_valueInstantiator");
        assertNotNull(inst);
        return inst;
    }
}
