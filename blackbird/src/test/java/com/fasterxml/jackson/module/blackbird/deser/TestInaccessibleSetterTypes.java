package com.fasterxml.jackson.module.blackbird.deser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.impl.MethodProperty;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;
import com.fasterxml.jackson.module.blackbird.deser.otherpackage.HiddenSetterBeanBase;
import com.fasterxml.jackson.module.blackbird.deser.otherpackage.ModeBeanBase;
import com.fasterxml.jackson.module.blackbird.deser.otherpackage.ModeBuilderBase;
import com.fasterxml.jackson.module.blackbird.deser.otherpackage.ProtectedSetterBeanBase;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.Chain;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.ChainBean;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.ChainBeanBase;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.ChainBuilder;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.ChainBuilderBase;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.ChainBuilt;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.Level;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.LevelBean;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.LevelBeanBase;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.LevelBuilder;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.LevelBuilderBase;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.LevelBuilt;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.OwnerBean;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.OwnerBuilder;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.OwnerBuilt;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.owner.OwnerBase;

import static org.junit.jupiter.api.Assertions.*;

// Setters whose generated accessor would fail when called, even though reflection
// can call them, must be left to reflection
public class TestInaccessibleSetterTypes extends BlackbirdTestBase
{
    // `setMode` and `setModes` take a type that is package-private in another
    // package, which reflection can pass but a generated accessor can't refer to
    static final class ModeBuilder extends ModeBuilderBase<ModeBuilder>
    {
        public ModeBuilt build() {
            return new ModeBuilt(mode, modes, name);
        }
    }

    @JsonDeserialize(builder=ModeBuilder.class)
    static class ModeBuilt
    {
        final String mode;
        final String modes;
        final String name;

        ModeBuilt(String mode, String modes, String name) {
            this.mode = mode;
            this.modes = modes;
            this.name = name;
        }
    }

    static final class ModeBean extends ModeBeanBase { }

    static final class ProtectedBean extends ProtectedSetterBeanBase { }

    static final class HiddenSetterBean extends HiddenSetterBeanBase { }

    private final ObjectMapper MAPPER = newObjectMapper();

    @Test
    public void testInaccessibleParameterType() throws Exception
    {
        ModeBeanBase.setterCalls = 0;
        ModeBean result = MAPPER.readValue(a2q("{'mode':'FAST','modes':['SAFE'],'name':'foo'}"), ModeBean.class);
        assertEquals("FAST", result.mode());
        assertEquals("[SAFE]", result.modes());
        assertEquals("foo", result.name());
        assertEquals(2, ModeBeanBase.setterCalls);
        // rather than failing (and falling back) on every call
        assertEquals(MethodProperty.class, _findProperty(ModeBean.class, "mode").getClass());
        assertEquals(MethodProperty.class, _findProperty(ModeBean.class, "modes").getClass());
        SettableBeanProperty name = _findProperty(ModeBean.class, "name");
        assertTrue(name instanceof OptimizedSettableBeanProperty, name.getClass().getName());
    }

    // `unreflect` refuses some inherited setters that reflection can call; these used
    // to fail the whole deserializer
    @Test
    public void testInheritedSettersFromAnotherPackage() throws Exception
    {
        assertEquals("foo", MAPPER.readValue(a2q("{'str':'foo'}"), ProtectedBean.class).str());
        assertEquals(3, MAPPER.readValue(a2q("{'y':3}"), HiddenSetterBean.class).y());
    }

    // The bean's loader has its own copy of `Level`, so a generated accessor would
    // resolve that one and fail to cast the inherited setter's `Level` to it
    @Test
    public void testShadowedParameterType() throws Exception
    {
        ClassLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                Arrays.asList(Level.class, LevelBean.class));
        Class<?> bean = Class.forName(LevelBean.class.getName(), true, loader);
        assertNotSame(LevelBean.class, bean);

        LevelBeanBase.setterCalls = 0;
        LevelBeanBase result = (LevelBeanBase) MAPPER.readValue(a2q("{'level':'HIGH'}"), bean);
        assertEquals("HIGH", result.level());
        assertEquals(1, LevelBeanBase.setterCalls);
        assertEquals(MethodProperty.class, _findProperty(bean, "level").getClass());
    }

    // Same for a fluent setter's return type: nothing casts to it, but calling the
    // setter names it, and the JVM requires both loaders to agree on it
    @Test
    public void testShadowedReturnType() throws Exception
    {
        ClassLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                Arrays.asList(Chain.class, ChainBean.class));
        Class<?> bean = Class.forName(ChainBean.class.getName(), true, loader);
        // the bean's loader has its own `Chain`, as it would once anything there used it
        assertNotSame(Chain.class, Class.forName(Chain.class.getName(), false, loader));

        ChainBeanBase.setterCalls = 0;
        ChainBeanBase result = (ChainBeanBase) MAPPER.readValue(a2q("{'level':'HIGH'}"), bean);
        assertEquals("HIGH", result.level());
        assertEquals(1, ChainBeanBase.setterCalls);
        assertEquals(MethodProperty.class, _findProperty(bean, "level").getClass());
    }

    // Nor may the check load the return type, which the generated setter never does:
    // the loader would define its own copy, and the code using it might then fail
    @Test
    public void testReturnTypeIsNotLoaded() throws Exception
    {
        ChildFirstLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                Arrays.asList(Chain.class, ChainBean.class));
        Class<?> bean = Class.forName(ChainBean.class.getName(), true, loader);

        ChainBeanBase.setterCalls = 0;
        ChainBeanBase result = (ChainBeanBase) MAPPER.readValue(a2q("{'level':'HIGH'}"), bean);
        assertEquals("HIGH", result.level());
        assertEquals(1, ChainBeanBase.setterCalls);
        assertFalse(loader.defined.contains(Chain.class.getName()));
    }

    // And for the declaring class of an inherited protected setter, which isn't in the
    // handle's type (its receiver is narrowed to the bean), but still named by the call
    @Test
    public void testShadowedDeclaringClass() throws Exception
    {
        ClassLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                Arrays.asList(OwnerBase.class, OwnerBean.class));
        Class<?> bean = Class.forName(OwnerBean.class.getName(), true, loader);
        assertNotSame(OwnerBase.class, Class.forName(OwnerBase.class.getName(), false, loader));
        // a lookup in the bean itself, which the protected setter needs
        MethodHandles.Lookup beanLookup = (MethodHandles.Lookup) bean.getMethod("lookup").invoke(null);
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new BlackbirdModule(c -> (c == bean) ? beanLookup : MethodHandles.lookup()))
                .build();

        OwnerBase.setterCalls = 0;
        Object result = mapper.readValue(a2q("{'value':7}"), bean);
        assertEquals(7, bean.getMethod("value").invoke(result));
        assertEquals(1, OwnerBase.setterCalls);
        assertEquals(MethodProperty.class, _findProperty(mapper, bean, "value").getClass());
    }

    // Nor may checking the declaring class load it, when the generated setter wouldn't
    // (or wouldn't exist at all, as for a `double` setter)
    @Test
    public void testDeclaringClassIsNotLoaded() throws Exception
    {
        for (boolean beanLookup : new boolean[] { false, true }) {
            ChildFirstLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                    Arrays.asList(OwnerBase.class, OwnerBean.class));
            Class<?> bean = Class.forName(OwnerBean.class.getName(), true, loader);
            ObjectMapper mapper = MAPPER;
            if (beanLookup) {
                MethodHandles.Lookup lookup = (MethodHandles.Lookup) bean.getMethod("lookup").invoke(null);
                mapper = JsonMapper.builder()
                        .addModule(new BlackbirdModule(c -> (c == bean) ? lookup : MethodHandles.lookup()))
                        .build();
            }
            Object result = mapper.readValue(a2q("{'value':7,'ratio':0.5}"), bean);
            assertEquals(7, bean.getMethod("value").invoke(result));
            assertEquals(0.5, bean.getMethod("ratio").invoke(result));
            assertFalse(loader.defined.contains(OwnerBase.class.getName()), "beanLookup=" + beanLookup);
        }
    }

    // A loader that fails (other than not finding the class) only stops the optimization
    @Test
    public void testFailingLoader() throws Exception
    {
        ClassLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                Arrays.asList(LevelBean.class), Collections.singleton(Level.class.getName()));
        Class<?> bean = Class.forName(LevelBean.class.getName(), true, loader);

        LevelBeanBase.setterCalls = 0;
        LevelBeanBase result = (LevelBeanBase) MAPPER.readValue(a2q("{'level':'HIGH'}"), bean);
        assertEquals("HIGH", result.level());
        assertEquals(1, LevelBeanBase.setterCalls);
        assertEquals(MethodProperty.class, _findProperty(bean, "level").getClass());
    }

    /*
    /**********************************************************************
    /* Builders, whose setters used to always go through reflection
    /**********************************************************************
     */

    @Test
    public void testInaccessibleParameterTypeOnBuilder() throws Exception
    {
        ModeBuilderBase.setterCalls = 0;
        ModeBuilt result = MAPPER.readValue(a2q("{'mode':'FAST','modes':['SAFE'],'name':'foo'}"),
                ModeBuilt.class);
        assertEquals("FAST", result.mode);
        assertEquals("[SAFE]", result.modes);
        assertEquals("foo", result.name);
        assertEquals(2, ModeBuilderBase.setterCalls);
        assertEquals(MethodProperty.class, _findProperty(ModeBuilt.class, "mode").getClass());
        assertEquals(MethodProperty.class, _findProperty(ModeBuilt.class, "modes").getClass());
        SettableBeanProperty name = _findProperty(ModeBuilt.class, "name");
        assertTrue(name instanceof OptimizedSettableBeanProperty, name.getClass().getName());
    }

    @Test
    public void testShadowedParameterTypeOnBuilder() throws Exception
    {
        ClassLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                Arrays.asList(Level.class, LevelBuilder.class, LevelBuilt.class));
        Class<?> built = Class.forName(LevelBuilt.class.getName(), true, loader);
        assertNotSame(LevelBuilt.class, built);

        LevelBuilderBase.setterCalls = 0;
        Object result = MAPPER.readValue(a2q("{'level':'HIGH'}"), built);
        assertEquals("HIGH", built.getField("level").get(result));
        assertEquals(1, LevelBuilderBase.setterCalls);
        assertEquals(MethodProperty.class, _findProperty(built, "level").getClass());
    }

    @Test
    public void testShadowedReturnTypeOnBuilder() throws Exception
    {
        ClassLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                Arrays.asList(Chain.class, ChainBuilder.class, ChainBuilt.class));
        Class<?> built = Class.forName(ChainBuilt.class.getName(), true, loader);
        assertNotSame(ChainBuilt.class, built);
        // the builder's loader has its own `Chain`, as it would once anything there used it
        assertNotSame(Chain.class, Class.forName(Chain.class.getName(), false, loader));

        ChainBuilderBase.setterCalls = 0;
        Object result = MAPPER.readValue(a2q("{'level':'HIGH'}"), built);
        assertEquals("HIGH", built.getField("level").get(result));
        assertEquals(1, ChainBuilderBase.setterCalls);
        assertEquals(MethodProperty.class, _findProperty(built, "level").getClass());
    }

    @Test
    public void testReturnTypeIsNotLoadedOnBuilder() throws Exception
    {
        ChildFirstLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                Arrays.asList(Chain.class, ChainBuilder.class, ChainBuilt.class));
        Class<?> built = Class.forName(ChainBuilt.class.getName(), true, loader);

        ChainBuilderBase.setterCalls = 0;
        Object result = MAPPER.readValue(a2q("{'level':'HIGH'}"), built);
        assertEquals("HIGH", built.getField("level").get(result));
        assertEquals(1, ChainBuilderBase.setterCalls);
        assertFalse(loader.defined.contains(Chain.class.getName()));
    }

    @Test
    public void testShadowedDeclaringClassOnBuilder() throws Exception
    {
        ClassLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                Arrays.asList(OwnerBase.class, OwnerBuilder.class, OwnerBuilt.class));
        Class<?> built = Class.forName(OwnerBuilt.class.getName(), true, loader);
        Class<?> builder = Class.forName(OwnerBuilder.class.getName(), true, loader);
        assertNotSame(OwnerBuilt.class, built);
        assertNotSame(OwnerBase.class, Class.forName(OwnerBase.class.getName(), false, loader));
        // a lookup in the builder itself, which the protected setter needs
        MethodHandles.Lookup builderLookup = (MethodHandles.Lookup) builder.getMethod("lookup").invoke(null);
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new BlackbirdModule(c -> (c == builder) ? builderLookup : MethodHandles.lookup()))
                .build();

        OwnerBase.setterCalls = 0;
        Object result = mapper.readValue(a2q("{'value':7}"), built);
        assertEquals(7, built.getField("value").get(result));
        assertEquals(1, OwnerBase.setterCalls);
        assertEquals(MethodProperty.class, _findProperty(mapper, built, "value").getClass());
    }

    @Test
    public void testFailingLoaderOnBuilder() throws Exception
    {
        ChildFirstLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                Arrays.asList(LevelBuilder.class, LevelBuilt.class),
                Collections.singleton(Level.class.getName()));
        Class<?> built = Class.forName(LevelBuilt.class.getName(), true, loader);

        LevelBuilderBase.setterCalls = 0;
        Object result = MAPPER.readValue(a2q("{'level':'HIGH'}"), built);
        assertEquals("HIGH", built.getField("level").get(result));
        assertEquals(1, LevelBuilderBase.setterCalls);
        assertEquals(MethodProperty.class, _findProperty(built, "level").getClass());
    }


    // Setup skips this accessor, so build it the same way here to check that the
    // access error the JVM really raises (from a hidden frame on Java 9+) is
    // recognized as the accessor's, and falls back without running the setter twice
    @Test
    public void testRealAccessorFailureFallsBack() throws Throwable
    {
        Class<?> mode = Class.forName(ModeBuilderBase.class.getPackage().getName() + ".Mode");
        MethodHandle setter = MethodHandles.lookup().unreflect(
                ModeBuilderBase.class.getMethod("withMode", mode));
        @SuppressWarnings("unchecked")
        BiFunction<Object, Object, Object> thunk = (BiFunction<Object, Object, Object>) LambdaMetafactory.metafactory(
                MethodHandles.lookup(), "apply",
                MethodType.methodType(BiFunction.class),
                MethodType.methodType(Object.class, Object.class, Object.class),
                setter, setter.type())
            .getTarget().invoke();
        BiConsumer<Object, Object> unused = (bean, value) -> fail("not a builder setter");

        ObjectMapper vanilla = newVanillaJSONMapper();
        DefaultDeserializationContext ctxt = ((DefaultDeserializationContext) vanilla.getDeserializationContext())
                .createInstance(vanilla.getDeserializationConfig(), null, null);
        BeanDeserializerBase deser = (BeanDeserializerBase) ctxt.findRootValueDeserializer(
                vanilla.constructType(ModeBuilt.class));
        SettableObjectProperty prop = new SettableObjectProperty(deser.findProperty("mode"), unused, thunk,
                setter.type().parameterType(0));

        Object fast = mode.getEnumConstants()[0];
        for (int i = 0; i < 2; i++) {
            ModeBuilderBase.setterCalls = 0;
            ModeBuilder builder = new ModeBuilder();
            assertThrows(IllegalAccessError.class, () -> thunk.apply(builder, fast));
            assertEquals(0, ModeBuilderBase.setterCalls);
            assertSame(builder, prop.setAndReturn(builder, fast));
            assertEquals("FAST", builder.build().mode);
            assertEquals(1, ModeBuilderBase.setterCalls);
        }
    }

    private SettableBeanProperty _findProperty(Class<?> type, String name) throws Exception
    {
        return _findProperty(MAPPER, type, name);
    }

    private static SettableBeanProperty _findProperty(ObjectMapper mapper, Class<?> type, String name)
        throws Exception
    {
        DefaultDeserializationContext ctxt = ((DefaultDeserializationContext) mapper.getDeserializationContext())
                .createInstance(mapper.getDeserializationConfig(), null, null);
        BeanDeserializerBase deser = (BeanDeserializerBase) ctxt.findRootValueDeserializer(
                mapper.constructType(type));
        return deser.findProperty(name);
    }

    // Defines its own copies of the given classes, delegating everything else, and
    // fails for the names it's told to
    static final class ChildFirstLoader extends ClassLoader
    {
        private final List<Class<?>> _copied;
        private final Set<String> _failing;
        final Set<String> defined = Collections.synchronizedSet(new HashSet<>());

        ChildFirstLoader(ClassLoader parent, List<Class<?>> copied) {
            this(parent, copied, Collections.emptySet());
        }

        ChildFirstLoader(ClassLoader parent, List<Class<?>> copied, Set<String> failing) {
            super(parent);
            _copied = copied;
            _failing = failing;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (_failing.contains(name)) {
                throw new IllegalStateException("loader closed: " + name);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    for (Class<?> copied : _copied) {
                        if (copied.getName().equals(name)) {
                            byte[] bytes = _bytesOf(copied);
                            c = defineClass(name, bytes, 0, bytes.length);
                            defined.add(name);
                        }
                    }
                }
                if (c == null) {
                    return super.loadClass(name, resolve);
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }

        private byte[] _bytesOf(Class<?> type) throws ClassNotFoundException {
            String resource = type.getName().replace('.', '/') + ".class";
            try (InputStream in = getParent().getResourceAsStream(resource)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            } catch (Exception e) {
                throw new ClassNotFoundException(type.getName(), e);
            }
        }
    }
}
