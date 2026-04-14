package tools.jackson.module.afterburner.inject;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.deser.SettableBeanProperty;

import static org.junit.jupiter.api.Assertions.*;

// Caching semantics for Afterburner's generated mutator classes. A regression in
// this area would not break functional behavior but would waste class metaspace
// and pin classloaders — so the only way to catch it is to directly inspect the
// generated-class identity.
//
// What's asserted:
//
//  1. Within a single mapper, all OptimizedSettableBeanProperty instances for
//     the *same* POJO share the *same* BeanPropertyMutator class. Afterburner
//     generates one mutator class per POJO and reuses it across every
//     optimized property of that POJO.
//
//  2. Across two independent mappers (each with its own AfterburnerModule
//     instance), the mutator class for the same POJO is the *same* Class
//     instance. This proves the generated class is cached on the bean's
//     parent classloader and not regenerated per mapper — a regression would
//     cause classloader bloat under load as short-lived mappers each leaked a
//     distinct generated class.
//
//  3. Two *different* POJOs — even ones with identical field shapes — get
//     distinct generated mutator classes. Afterburner keys cache entries on
//     the bean class name, not on bytecode shape. A regression that conflated
//     them would silently deserialize one POJO's JSON into the other's fields.
public class GeneratedClassCachingTest extends AfterburnerInjectionTestBase
{
    public static class FooBean {
        public int a;
        public String b;
    }

    // Structurally identical to FooBean, intentionally — same field names and
    // types. Afterburner must still generate a distinct mutator for it because
    // the generated class name is derived from the bean class name.
    public static class BarBean {
        public int a;
        public String b;
    }

    @Test
    public void testMutatorSharedAcrossPropertiesOfSameBean() throws Exception
    {
        Harness h = newHarness();
        h.mapper.readValue("{\"a\":1,\"b\":\"x\"}", FooBean.class);

        SettableBeanProperty[] props = propsOf(h.deserFor(FooBean.class));
        assertEquals(2, props.length);

        Object mutatorA = reflectField(props[0], "_propertyMutator");
        Object mutatorB = reflectField(props[1], "_propertyMutator");
        assertNotNull(mutatorA);
        assertNotNull(mutatorB);
        assertSame(mutatorA.getClass(), mutatorB.getClass(),
                "all properties of the same POJO should share one mutator class; got "
                        + mutatorA.getClass().getName() + " vs "
                        + mutatorB.getClass().getName());
    }

    @Test
    public void testSameBeanAcrossMappersReusesSameMutatorClass() throws Exception
    {
        Harness h1 = newHarness();
        Harness h2 = newHarness();

        h1.mapper.readValue("{\"a\":1,\"b\":\"x\"}", FooBean.class);
        h2.mapper.readValue("{\"a\":2,\"b\":\"y\"}", FooBean.class);

        Class<?> mutatorClass1 = mutatorClassFor(h1, FooBean.class);
        Class<?> mutatorClass2 = mutatorClassFor(h2, FooBean.class);

        assertSame(mutatorClass1, mutatorClass2,
                "two independent mappers should share the same cached mutator class "
                        + "for FooBean; got " + mutatorClass1.getName() + " vs "
                        + mutatorClass2.getName() + " — afterburner may be regenerating "
                        + "classes instead of caching them on the parent classloader");
    }

    @Test
    public void testDifferentBeansGetDistinctMutatorClasses() throws Exception
    {
        Harness h = newHarness();
        h.mapper.readValue("{\"a\":1,\"b\":\"x\"}", FooBean.class);
        h.mapper.readValue("{\"a\":2,\"b\":\"y\"}", BarBean.class);

        Class<?> fooMutator = mutatorClassFor(h, FooBean.class);
        Class<?> barMutator = mutatorClassFor(h, BarBean.class);

        assertNotSame(fooMutator, barMutator,
                "structurally identical but distinct POJOs must get distinct mutator "
                        + "classes (otherwise their property ordering could collide); "
                        + "both got " + fooMutator.getName());

        // Extra sanity: the generated class names should reflect the bean class names.
        assertTrue(fooMutator.getName().contains("FooBean"),
                "expected FooBean's mutator class name to mention FooBean; got "
                        + fooMutator.getName());
        assertTrue(barMutator.getName().contains("BarBean"),
                "expected BarBean's mutator class name to mention BarBean; got "
                        + barMutator.getName());
    }

    /** Reads the `_propertyMutator` field off the first property of the captured
     *  deserializer and returns its runtime class. */
    private static Class<?> mutatorClassFor(Harness h, Class<?> beanClass) {
        SettableBeanProperty[] props = propsOf(h.deserFor(beanClass));
        assertTrue(props.length > 0, "no properties for " + beanClass.getSimpleName());
        Object mutator = reflectField(props[0], "_propertyMutator");
        assertNotNull(mutator,
                "property '" + props[0].getName() + "' has no _propertyMutator — "
                        + "afterburner did not install a generated mutator");
        return mutator.getClass();
    }
}
