package tools.jackson.module.blackbird.deser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// The beans record calls and failures in static fields, so tests must not run concurrently
@Execution(ExecutionMode.SAME_THREAD)
public class TestSetterFailures extends BlackbirdTestBase
{
    // for calling `set()` directly
    private static final DeserializationContext CTXT = new JsonMapper()._deserializationContext();

    private static Supplier<Throwable> failure;
    private static Throwable lastFailure;
    // not private: the hidden classes below aren't nestmates, so can't access it
    static int calls;

    static class VoidBean {
        public void setStr(String value) { failOnce(); }
        public void setObj(List<String> value) { failOnce(); }
        public void setI(int value) { failOnce(); }
        public void setL(long value) { failOnce(); }
        public void setB(boolean value) { failOnce(); }
    }

    static class FluentBean {
        public FluentBean setStr(String value) { failOnce(); return this; }
        public FluentBean setObj(List<String> value) { failOnce(); return this; }
        public FluentBean setI(int value) { failOnce(); return this; }
        public FluentBean setL(long value) { failOnce(); return this; }
        public FluentBean setB(boolean value) { failOnce(); return this; }
    }

    // Always fail, so reporting can be compared with plain Jackson
    private static Throwable alwaysThrow;

    static class AlwaysFailingBean {
        public void setStr(String value) { alwaysFail(); }
        public void setI(int value) { alwaysFail(); }
    }

    private static void alwaysFail() {
        calls++;
        if (alwaysThrow instanceof Error) {
            throw (Error) alwaysThrow;
        }
        throw (RuntimeException) alwaysThrow;
    }

    // `set()` skips nulls as `MethodProperty.set` does; properties before the creator's
    // are buffered and assigned through it
    static class SkipNullsBean {
        final int id;
        String str = "default";
        List<String> list = Arrays.asList("keep");
        int i = 7;
        long l = 8L;
        boolean b = true;

        @JsonCreator
        SkipNullsBean(@JsonProperty("id") int id) { this.id = id; }

        @JsonSetter(nulls=Nulls.SKIP)
        public void setI(int value) { calls++; i = value; }

        @JsonSetter(nulls=Nulls.SKIP)
        public void setL(long value) { calls++; l = value; }

        @JsonSetter(nulls=Nulls.SKIP)
        public void setB(boolean value) { calls++; b = value; }

        @JsonSetter(nulls=Nulls.SKIP)
        public void setStr(String value) { calls++; str = value; }

        @JsonSetter(nulls=Nulls.SKIP)
        public void setList(List<String> value) { calls++; list = value; }
    }

    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME, defaultImpl=Void.class)
    static class Item {
        public String name;
    }

    static class PolymorphicNullBean {
        int calls;
        Item value = new Item();

        @JsonSetter(nulls=Nulls.SKIP)
        public void setVoid(Item v) { calls++; value = v; }

        @JsonSetter(nulls=Nulls.SKIP)
        public PolymorphicNullBean setFluent(Item v) { calls++; value = v; return this; }
    }

    // The bean is a hidden subclass, whose setters are left out of stack traces as
    // the generated accessors are
    private static Class<?> hiddenVoid, hiddenFluent;

    public static class HideableVoidBean {
        @JsonCreator
        public static HideableVoidBean create() throws Exception {
            return (HideableVoidBean) hiddenVoid.getConstructor().newInstance();
        }
        public void setStr(String value) { }
    }

    public static class HiddenVoidBean extends HideableVoidBean {
        public HiddenVoidBean() { }
        @Override
        public void setStr(String value) { calls++; throw new SecurityException("thrown by hidden setter"); }
    }

    public static class HideableFluentBean {
        @JsonCreator
        public static HideableFluentBean create() throws Exception {
            return (HideableFluentBean) hiddenFluent.getConstructor().newInstance();
        }
        public HideableFluentBean setStr(String value) { return this; }
    }

    public static class HiddenFluentBean extends HideableFluentBean {
        public HiddenFluentBean() { }
        @Override
        public HideableFluentBean setStr(String value) { calls++; throw new SecurityException("thrown by hidden setter"); }
    }

    // A retry would succeed and hide the original failure.
    private static void failOnce() {
        if (calls++ == 0) {
            lastFailure = failure.get();
            if (lastFailure instanceof Error) {
                throw (Error) lastFailure;
            }
            throw (RuntimeException) lastFailure;
        }
    }

    private List<Supplier<Throwable>> failures() {
        SecurityException savedSecurity = new SecurityException("saved");
        IllegalAccessError savedAccess = new IllegalAccessError("saved");
        SecurityException emptySecurity = new SecurityException("no trace");
        emptySecurity.setStackTrace(new StackTraceElement[0]);
        IllegalAccessError emptyAccess = new IllegalAccessError("no trace");
        emptyAccess.setStackTrace(new StackTraceElement[0]);
        return Arrays.asList(
                () -> new SecurityException("thrown by setter"),
                () -> new IllegalAccessError("thrown by setter"),
                () -> savedSecurity,
                () -> savedAccess,
                () -> emptySecurity,
                () -> emptyAccess);
    }

    @Test
    public void testDeserializeDoesNotRetrySetterFailures() throws Exception {
        for (boolean wrap : new boolean[] { true, false }) {
            ObjectMapper mapper = mapperBuilder()
                    .configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap).build();
            for (Class<?> beanType : new Class<?>[] { VoidBean.class, FluentBean.class }) {
                for (String json : new String[] { a2q("{'str':'foo'}"), a2q("{'obj':['foo']}"),
                        a2q("{'i':42}"), a2q("{'l':12345678901}"), a2q("{'b':true}") }) {
                    for (Supplier<Throwable> nextFailure : failures()) {
                        failure = nextFailure;
                        assertRunsOnce(() -> mapper.readValue(json, beanType),
                                beanType.getSimpleName() + ", wrap=" + wrap + ", " + json);
                    }
                }
            }
        }
    }

    @Test
    public void testSetDoesNotRetrySetterFailures() throws Exception {
        ObjectMapper mapper = newObjectMapper();
        String[] names = { "str", "obj", "i", "l", "b" };
        Object[] values = { "foo", Arrays.asList("foo"), 42, 12345678901L, true };
        List<Supplier<Object>> beans = Arrays.asList(VoidBean::new, FluentBean::new);
        for (Supplier<Object> newBean : beans) {
            Class<?> beanType = newBean.get().getClass();
            assertGeneratedReader(mapper, beanType);
            for (int i = 0; i < names.length; i++) {
                SettableBeanProperty property = property(mapper, beanType, names[i]);
                Object value = values[i];
                for (Supplier<Throwable> nextFailure : failures()) {
                    failure = nextFailure;
                    assertRunsOnce(() -> property.set(CTXT, newBean.get(), value),
                            beanType.getSimpleName() + ", " + names[i]);
                }
            }
        }
    }

    // Not retried, and reported as `MethodProperty` reports them (which in 3.x
    // rethrows the setter's `Error`s and, unless wrapped, runtime exceptions as is)
    @Test
    public void testSetterFailuresAreReportedAsVanilla() throws Exception {
        for (boolean wrap : new boolean[] { true, false }) {
            ObjectMapper vanilla = JsonMapper.builder().configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap).build();
            ObjectMapper blackbird = mapperBuilder().configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap).build();
            for (Throwable t : new Throwable[] { new SecurityException("se"), new IllegalAccessError("iae"),
                    new IllegalStateException("ise"), new NoClassDefFoundError("ncdfe"), new AssertionError("ae") }) {
                for (String json : new String[] { a2q("{'str':'foo'}"), a2q("{'i':42}") }) {
                    String desc = "wrap=" + wrap + ", " + t + ", " + json;
                    alwaysThrow = t;
                    calls = 0;
                    Throwable expected = assertThrows(Throwable.class, () -> vanilla.readValue(json, AlwaysFailingBean.class));
                    Throwable actual = assertThrows(Throwable.class, () -> blackbird.readValue(json, AlwaysFailingBean.class));
                    assertEquals(2, calls, desc);
                    assertEquals(expected.getClass(), actual.getClass(), desc);
                    if (expected instanceof DatabindException) {
                        DatabindException e = (DatabindException) expected;
                        DatabindException a = (DatabindException) actual;
                        assertEquals(e.getOriginalMessage(), a.getOriginalMessage(), desc);
                        assertEquals(e.getPathReference(), a.getPathReference(), desc);
                        assertEquals(e.getLocation(), a.getLocation(), desc);
                        assertSame(t, a.getCause(), desc);
                    } else {
                        assertSame(t, actual, desc);
                    }
                }
            }
        }
    }

    @Test
    public void testSetSkipsNulls() throws Exception {
        for (String name : new String[] { "str", "list", "i", "l", "b" }) {
            SettableBeanProperty property = property(newObjectMapper(), SkipNullsBean.class, name);
            SkipNullsBean bean = new SkipNullsBean(1);
            calls = 0;
            property.set(CTXT, bean, null);
            assertEquals(0, calls, name);
        }
        // with the nulls before the creator property, so they're buffered
        String json = a2q("{'str':null,'list':null,'i':null,'l':null,'b':null,'id':1}");
        for (ObjectMapper mapper : new ObjectMapper[] { newVanillaJSONMapper(), newObjectMapper() }) {
            calls = 0;
            SkipNullsBean bean = mapper.readValue(json, SkipNullsBean.class);
            assertEquals("default", bean.str);
            assertEquals(Arrays.asList("keep"), bean.list);
            assertEquals(7, bean.i);
            assertEquals(8L, bean.l);
            assertTrue(bean.b);
            assertEquals(0, calls);
        }
    }

    // Without skipping, a null for a primitive fails as it does through reflection
    @Test
    public void testSetNullPrimitiveWithoutSkipping() throws Exception {
        ObjectMapper vanilla = newVanillaJSONMapper();
        ObjectMapper blackbird = newObjectMapper();
        for (String name : new String[] { "i", "l", "b" }) {
            SettableBeanProperty expectedProperty = property(vanilla, VoidBean.class, name);
            SettableBeanProperty actualProperty = property(blackbird, VoidBean.class, name);
            calls = 0;
            Throwable expected = assertThrows(Throwable.class,
                    () -> expectedProperty.set(CTXT, new VoidBean(), null), name);
            Throwable actual = assertThrows(Throwable.class,
                    () -> actualProperty.set(CTXT, new VoidBean(), null), name);
            assertEquals(expected.getClass(), actual.getClass(), name);
            assertEquals(expected.getMessage(), actual.getMessage(), name);
            assertEquals(0, calls, name);
        }
    }

    @Test
    public void testPolymorphicNullMatchesVanilla() throws Exception {
        ObjectMapper blackbird = newObjectMapper();
        assertGeneratedReader(blackbird, PolymorphicNullBean.class);
        for (String name : new String[] { "void", "fluent" }) {
            SettableBeanProperty prop = property(blackbird, PolymorphicNullBean.class, name);

            PolymorphicNullBean direct = new PolymorphicNullBean();
            prop.set(CTXT, direct, null);
            assertEquals(0, direct.calls, name);
            assertNotNull(direct.value, name);

            for (ObjectMapper mapper : new ObjectMapper[] { newVanillaJSONMapper(), blackbird }) {
                PolymorphicNullBean explicit = mapper.readValue(a2q("{'" + name + "':null}"), PolymorphicNullBean.class);
                assertEquals(0, explicit.calls, name);
                assertNotNull(explicit.value, name);

                // MethodProperty passes a null from polymorphic deserialization to
                // the setter, even with Nulls.SKIP.
                PolymorphicNullBean polymorphic = mapper.readValue(
                        a2q("{'" + name + "':{'@type':'Unknown'}}"), PolymorphicNullBean.class);
                assertEquals(1, polymorphic.calls, name);
                assertNull(polymorphic.value, name);
            }
        }
    }

    @Test
    public void testHiddenBeanSetterFailureIsNotRetried() throws Exception {
        hiddenVoid = defineHidden(HiddenVoidBean.class);
        hiddenFluent = defineHidden(HiddenFluentBean.class);
        for (Class<?> type : new Class<?>[] { HideableVoidBean.class, HideableFluentBean.class }) {
            assertGeneratedReader(newObjectMapper(), type);
            for (ObjectMapper mapper : new ObjectMapper[] { newVanillaJSONMapper(), newObjectMapper() }) {
                calls = 0;
                assertThrows(DatabindException.class, () -> mapper.readValue(a2q("{'str':'foo'}"), type));
                assertEquals(1, calls, type.getSimpleName());
            }
        }
    }

    private static Class<?> defineHidden(Class<?> type) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        }
        return MethodHandles.lookup().defineHiddenClass(out.toByteArray(), true).lookupClass();
    }

    private void assertRunsOnce(Executable action, String description) {
        calls = 0;
        Throwable thrown = assertThrows(Throwable.class, action, description);
        assertEquals(1, calls, description);
        while (thrown.getCause() != null) {
            thrown = thrown.getCause();
        }
        assertSame(lastFailure, thrown, description);
    }

    private static SettableBeanProperty property(ObjectMapper mapper, Class<?> beanType, String name) throws Exception {
        BeanDeserializerBase deserializer = (BeanDeserializerBase) mapper._deserializationContext()
                .findRootValueDeserializer(mapper.constructType(beanType));
        return deserializer.findProperty(PropertyName.construct(name));
    }
}
