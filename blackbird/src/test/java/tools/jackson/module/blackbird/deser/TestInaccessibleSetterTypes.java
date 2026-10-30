package tools.jackson.module.blackbird.deser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;
import tools.jackson.module.blackbird.deser.isolated.Token;
import tools.jackson.module.blackbird.deser.isolated.TokenBean;
import tools.jackson.module.blackbird.deser.isolated.TokenBuilder;
import tools.jackson.module.blackbird.deser.isolated.TokenBuilt;
import tools.jackson.module.blackbird.deser.otherpackage.HiddenSetterBeanBase;
import tools.jackson.module.blackbird.deser.otherpackage.ModeBeanBase;
import tools.jackson.module.blackbird.deser.otherpackage.ProtectedSetterBeanBase;
import tools.jackson.module.blackbird.deser.shadowed.Chain;
import tools.jackson.module.blackbird.deser.shadowed.ChainBean;
import tools.jackson.module.blackbird.deser.shadowed.ChainBeanBase;
import tools.jackson.module.blackbird.deser.shadowed.DirectLevelBean;
import tools.jackson.module.blackbird.deser.shadowed.Level;
import tools.jackson.module.blackbird.deser.shadowed.LevelBean;
import tools.jackson.module.blackbird.deser.shadowed.LevelBeanBase;
import tools.jackson.module.blackbird.deser.shadowed.OwnerBean;
import tools.jackson.module.blackbird.deser.shadowed.owner.OwnerBase;

import static org.junit.jupiter.api.Assertions.*;

// Setters that generated code could easily get wrong, though reflection calls them
// fine: their types are inaccessible, shadowed by the bean's loader, or must not be
// loaded. Generated readers must handle them as stock does, without loading anything.
// (From #366, which fixed these for the earlier LambdaMetafactory engine.)
public class TestInaccessibleSetterTypes extends BlackbirdTestBase
{
    // `setMode` and `setModes` take a type that is package-private in another
    // package, which reflection can pass but generated code can't name
    static final class ModeBean extends ModeBeanBase { }

    static final class ProtectedBean extends ProtectedSetterBeanBase { }

    static final class HiddenSetterBean extends HiddenSetterBeanBase { }

    // Common setter signatures, including fluent ones and types from other loaders
    // (JDK `javax.*` ones)
    static class CommonSignatures
    {
        String a;
        List<String> b;
        int c;

        public Object setA(String v) { a = v; return this; }
        public CharSequence setB(List<String> v) { b = v; return "x"; }
        public Integer setC(int v) { c = v; return v; }
        public javax.xml.namespace.QName setD(String v) { return null; }
    }

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
        assertGeneratedReader(MAPPER, ModeBean.class);
    }

    // Inherited setters that only reflection, or a lookup in the subclass, can call
    @Test
    public void testInheritedSettersFromAnotherPackage() throws Exception
    {
        assertEquals("foo", MAPPER.readValue(a2q("{'str':'foo'}"), ProtectedBean.class).str());
        assertEquals(3, MAPPER.readValue(a2q("{'y':3}"), HiddenSetterBean.class).y());
    }

    // The bean's loader has its own copy of `Level`, so code resolving it from there
    // would fail to pass it to the inherited setter, which takes the parent's
    @Test
    public void testShadowedParameterType() throws Exception
    {
        for (boolean reads : READS) {
            ClassLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                    Arrays.asList(Level.class, LevelBean.class));
            Class<?> bean = load(loader, LevelBean.class, reads);
            assertNotSame(LevelBean.class, bean);

            LevelBeanBase.setterCalls = 0;
            LevelBeanBase result = (LevelBeanBase) MAPPER.readValue(a2q("{'level':'HIGH'}"), bean);
            assertEquals("HIGH", result.level());
            assertEquals(1, LevelBeanBase.setterCalls);
            assertGeneratedReader(MAPPER, bean);
        }
    }

    // Same for a fluent setter's return type: nothing casts to it, but a call naming it
    // requires both loaders to agree on it
    @Test
    public void testShadowedReturnType() throws Exception
    {
        for (boolean reads : READS) {
            ClassLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                    Arrays.asList(Chain.class, ChainBean.class));
            Class<?> bean = load(loader, ChainBean.class, reads);
            // the bean's loader has its own `Chain`, as it would once anything there used it
            assertNotSame(Chain.class, Class.forName(Chain.class.getName(), false, loader));

            ChainBeanBase.setterCalls = 0;
            ChainBeanBase result = (ChainBeanBase) MAPPER.readValue(a2q("{'level':'HIGH'}"), bean);
            assertEquals("HIGH", result.level());
            assertEquals(1, ChainBeanBase.setterCalls);
            assertGeneratedReader(MAPPER, bean);
        }
    }

    // Nor may Blackbird load the return type, which stock never does: the loader would
    // define its own copy, and the code using it might then fail
    @Test
    public void testReturnTypeIsNotLoaded() throws Exception
    {
        for (boolean reads : READS) {
            ChildFirstLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                    Arrays.asList(Chain.class, ChainBean.class));
            Class<?> bean = load(loader, ChainBean.class, reads);

            ChainBeanBase.setterCalls = 0;
            ChainBeanBase result = (ChainBeanBase) MAPPER.readValue(a2q("{'level':'HIGH'}"), bean);
            assertEquals("HIGH", result.level());
            assertEquals(1, ChainBeanBase.setterCalls);
            assertFalse(loader.defined.contains(Chain.class.getName()), "reads=" + reads);
        }
    }

    // Same for the declaring class of an inherited protected setter
    @Test
    public void testShadowedDeclaringClass() throws Exception
    {
        for (boolean reads : READS) {
            ClassLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                    Arrays.asList(OwnerBase.class, OwnerBean.class));
            Class<?> bean = load(loader, OwnerBean.class, reads);
            assertNotSame(OwnerBase.class, Class.forName(OwnerBase.class.getName(), false, loader));

            OwnerBase.setterCalls = 0;
            Object result = MAPPER.readValue(a2q("{'value':7}"), bean);
            assertEquals(7, bean.getMethod("value").invoke(result));
            assertEquals(1, OwnerBase.setterCalls);
            assertGeneratedReader(MAPPER, bean);
        }
    }

    // Nor may Blackbird load the declaring class (including for a `double` setter)
    @Test
    public void testDeclaringClassIsNotLoaded() throws Exception
    {
        for (boolean reads : READS) {
            ChildFirstLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                    Arrays.asList(OwnerBase.class, OwnerBean.class));
            Class<?> bean = load(loader, OwnerBean.class, reads);
            Object result = MAPPER.readValue(a2q("{'value':7,'ratio':0.5}"), bean);
            assertEquals(7, bean.getMethod("value").invoke(result));
            assertEquals(0.5, bean.getMethod("ratio").invoke(result));
            assertFalse(loader.defined.contains(OwnerBase.class.getName()), "reads=" + reads);
        }
    }

    // Nor a parameter type from another loader: the copy it would define can't be
    // undone, and the code relying on that loader then fails
    @Test
    public void testParameterTypeIsNotLoaded() throws Exception
    {
        for (ObjectMapper mapper : new ObjectMapper[] { newVanillaJSONMapper(), MAPPER }) {
            for (boolean reads : READS) {
                ChildFirstLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                        Arrays.asList(Token.class, TokenBuilder.class, TokenBuilt.class));
                Class<?> built = load(loader, TokenBuilt.class, reads);
                Object result = mapper.readValue(a2q("{'token':'YES'}"), built);
                // the builder's own call to the inherited setter still works
                assertEquals(2, built.getField("calls").get(result));
                assertFalse(loader.defined.contains(Token.class.getName()), "reads=" + reads);

                loader = new ChildFirstLoader(getClass().getClassLoader(),
                        Arrays.asList(Token.class, TokenBean.class));
                Class<?> bean = load(loader, TokenBean.class, reads);
                // the bean's constructor calls the inherited method, with no setter called at all
                mapper.readValue("{}", bean);
                assertFalse(loader.defined.contains(Token.class.getName()), "reads=" + reads);
            }
        }
    }

    @Test
    public void testCommonSignaturesStayOptimized() throws Exception
    {
        assertGeneratedReader(MAPPER, CommonSignatures.class);
        CommonSignatures result = MAPPER.readValue(a2q("{'a':'x','b':['y'],'c':3,'d':'z'}"), CommonSignatures.class);
        assertEquals("x", result.a);
        assertEquals(Arrays.asList("y"), result.b);
        assertEquals(3, result.c);
    }

    // A setter declared by the bean itself, in a child loader, taking a type from the
    // parent
    @Test
    public void testParentLoaderParameterTypeStaysOptimized() throws Exception
    {
        for (boolean reads : READS) {
            ClassLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                    Arrays.asList(DirectLevelBean.class));
            Class<?> bean = load(loader, DirectLevelBean.class, reads);
            assertNotSame(DirectLevelBean.class, bean);
            assertSame(Level.class, Class.forName(Level.class.getName(), false, loader));

            assertGeneratedReader(MAPPER, bean);
            Object result = MAPPER.readValue(a2q("{'level':'HIGH'}"), bean);
            assertEquals(Level.HIGH, bean.getMethod("level").invoke(result));
        }
    }

    // A loader that fails (other than not finding the class) for a type Blackbird
    // needn't load
    @Test
    public void testFailingLoader() throws Exception
    {
        for (boolean reads : READS) {
            ClassLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                    Arrays.asList(LevelBean.class), Collections.singleton(Level.class.getName()));
            Class<?> bean = load(loader, LevelBean.class, reads);

            LevelBeanBase.setterCalls = 0;
            LevelBeanBase result = (LevelBeanBase) MAPPER.readValue(a2q("{'level':'HIGH'}"), bean);
            assertEquals("HIGH", result.level());
            assertEquals(1, LevelBeanBase.setterCalls);
            assertGeneratedReader(MAPPER, bean);
        }
    }

    // Blackbird, a named module here, doesn't read a child loader's unnamed module
    // unless told to, which changes the lookups it can get in the bean. Tests run both
    // ways.
    public static final boolean[] READS = { false, true };

    public static Class<?> load(ClassLoader loader, Class<?> type, boolean reads) throws Exception {
        Class<?> loaded = Class.forName(type.getName(), true, loader);
        if (reads) {
            TestInaccessibleSetterTypes.class.getModule().addReads(loaded.getModule());
            assertTrue(lookupInBean(loaded));
        }
        return loaded;
    }

    public static boolean lookupInBean(Class<?> bean) {
        return BlackbirdModule.class.getModule().canRead(bean.getModule());
    }

    // Defines its own copies of the given classes, delegating everything else, and
    // fails for the names it's told to
    public static final class ChildFirstLoader extends ClassLoader
    {
        private final List<Class<?>> _copied;
        private final Set<String> _failing;
        public final Set<String> defined = Collections.synchronizedSet(new HashSet<>());

        public ChildFirstLoader(ClassLoader parent, List<Class<?>> copied) {
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
