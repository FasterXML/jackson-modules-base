package com.fasterxml.jackson.module.blackbird.deser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;
import com.fasterxml.jackson.module.blackbird.deser.access.InaccessibleSetterValue;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class TestSetterFailures extends BlackbirdTestBase
{
    private static Supplier<Throwable> failure;
    private static Throwable lastFailure;
    private static int calls;

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

    static class NullTextBean {
        String skipped = "default";
        String plain = "default";

        @JsonSetter(nulls=Nulls.SKIP)
        public void setSkipped(String value) { skipped = value; }

        public void setPlain(String value) { plain = value; }
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
            for (int i = 0; i < names.length; i++) {
                SettableBeanProperty property = property(mapper, beanType, names[i]);
                assertTrue(property instanceof OptimizedSettableBeanProperty, names[i]);
                Object value = values[i];
                for (Supplier<Throwable> nextFailure : failures()) {
                    failure = nextFailure;
                    assertRunsOnce(() -> property.set(newBean.get(), value),
                            beanType.getSimpleName() + ", " + names[i]);
                }
            }
        }
    }

    // Not retried, these are still reported as `MethodProperty` reports them (as they
    // were after the retry), not as the setter's raw `Error` or exception
    @Test
    public void testAccessErrorsAreReportedAsVanilla() throws Exception {
        for (boolean wrap : new boolean[] { true, false }) {
            ObjectMapper vanilla = JsonMapper.builder().configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap).build();
            ObjectMapper blackbird = mapperBuilder().configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap).build();
            for (Throwable t : new Throwable[] { new SecurityException("se"), new IllegalAccessError("iae") }) {
                for (String json : new String[] { a2q("{'str':'foo'}"), a2q("{'i':42}") }) {
                    String desc = "wrap=" + wrap + ", " + t + ", " + json;
                    alwaysThrow = t;
                    calls = 0;
                    Throwable expected = assertThrows(Throwable.class, () -> vanilla.readValue(json, AlwaysFailingBean.class));
                    Throwable actual = assertThrows(Throwable.class, () -> blackbird.readValue(json, AlwaysFailingBean.class));
                    assertEquals(2, calls, desc);
                    assertEquals(expected.getClass(), actual.getClass(), desc);
                    JsonMappingException e = (JsonMappingException) expected;
                    JsonMappingException a = (JsonMappingException) actual;
                    assertEquals(e.getOriginalMessage(), a.getOriginalMessage(), desc);
                    assertEquals(e.getPathReference(), a.getPathReference(), desc);
                    assertEquals(e.getLocation(), a.getLocation(), desc);
                    assertSame(t, a.getCause(), desc);
                }
            }
        }
    }

    @Test
    public void testSetSkipsNulls() throws Exception {
        for (String name : new String[] { "str", "list", "i", "l", "b" }) {
            SettableBeanProperty property = property(newObjectMapper(), SkipNullsBean.class, name);
            assertTrue(property instanceof OptimizedSettableBeanProperty, name);
            SkipNullsBean bean = new SkipNullsBean(1);
            calls = 0;
            property.set(bean, null);
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
            assertTrue(actualProperty instanceof OptimizedSettableBeanProperty, name);
            calls = 0;
            JsonMappingException expected = assertThrows(JsonMappingException.class,
                    () -> expectedProperty.set(new VoidBean(), null), name);
            JsonMappingException actual = assertThrows(JsonMappingException.class,
                    () -> actualProperty.set(new VoidBean(), null), name);
            assertEquals(expected.getOriginalMessage(), actual.getOriginalMessage(), name);
            assertEquals(expected.getCause().getClass(), actual.getCause().getClass(), name);
            assertEquals(0, calls, name);
        }
    }

    @Test
    public void testPolymorphicNullMatchesVanilla() throws Exception {
        ObjectMapper blackbird = newObjectMapper();
        for (String name : new String[] { "void", "fluent" }) {
            SettableBeanProperty prop = property(blackbird, PolymorphicNullBean.class, name);
            assertTrue(prop instanceof OptimizedSettableBeanProperty, name);

            PolymorphicNullBean direct = new PolymorphicNullBean();
            prop.set(direct, null);
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

    // `TextNode(null)` reads as a VALUE_STRING whose text is null
    @Test
    public void testNullTextMatchesVanilla() throws Exception {
        assertTrue(property(newObjectMapper(), NullTextBean.class, "skipped") instanceof OptimizedSettableBeanProperty);
        ObjectNode tree = JsonNodeFactory.instance.objectNode();
        tree.set("skipped", new TextNode(null));
        tree.set("plain", new TextNode(null));
        for (ObjectMapper mapper : new ObjectMapper[] { newVanillaJSONMapper(), newObjectMapper() }) {
            NullTextBean bean = mapper.treeToValue(tree, NullTextBean.class);
            assertEquals("default", bean.skipped);
            assertNull(bean.plain);
        }
    }

    @Test
    public void testHiddenBeanSetterFailureIsNotRetried() throws Exception {
        Method define;
        Object noOptions;
        try {
            Class<?> option = Class.forName("java.lang.invoke.MethodHandles$Lookup$ClassOption");
            noOptions = Array.newInstance(option, 0);
            define = MethodHandles.Lookup.class.getMethod("defineHiddenClass",
                    byte[].class, boolean.class, noOptions.getClass());
        } catch (ReflectiveOperationException e) {
            assumeTrue(false, "hidden classes need Java 15+");
            return;
        }
        hiddenVoid = defineHidden(define, noOptions, HiddenVoidBean.class);
        hiddenFluent = defineHidden(define, noOptions, HiddenFluentBean.class);
        for (Class<?> type : new Class<?>[] { HideableVoidBean.class, HideableFluentBean.class }) {
            assertTrue(property(newObjectMapper(), type, "str") instanceof OptimizedSettableBeanProperty);
            for (ObjectMapper mapper : new ObjectMapper[] { newVanillaJSONMapper(), newObjectMapper() }) {
                calls = 0;
                assertThrows(JsonMappingException.class, () -> mapper.readValue(a2q("{'str':'foo'}"), type));
                assertEquals(1, calls, type.getSimpleName());
            }
        }
    }

    private static Class<?> defineHidden(Method define, Object noOptions, Class<?> type) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        }
        return ((MethodHandles.Lookup) define.invoke(MethodHandles.lookup(), out.toByteArray(), true, noOptions))
                .lookupClass();
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

    @Test
    public void testVoidAccessorAccessFailureIsNotRetried() throws Throwable {
        InaccessibleSetterValue bean = new InaccessibleSetterValue();
        Object value = InaccessibleSetterValue.newValue();
        Method setter = InaccessibleSetterValue.class.getMethod("setVoidValue", value.getClass());
        BiConsumer<Object, Object> accessor = voidAccessor(setter);
        SettableObjectProperty property = new SettableObjectProperty(
                property(newVanillaJSONMapper(), bean.getClass(), "voidValue"), accessor, null,
                InaccessibleSetterValue.class, setter.getParameterTypes()[0], false);

        // The generated accessor cannot cast to the package-private parameter type;
        // that is reported like the setter's failure, and the setter never runs
        assertThrows(IllegalAccessError.class, () -> accessor.accept(bean, value));
        JsonMappingException e = assertThrows(JsonMappingException.class, () -> property.set(bean, value));
        assertEquals(IllegalAccessError.class, e.getCause().getClass());
        assertEquals(0, bean.calls);
    }

    @Test
    public void testFluentAccessorAccessFailureIsNotRetried() throws Throwable {
        InaccessibleSetterValue bean = new InaccessibleSetterValue();
        Object value = InaccessibleSetterValue.newValue();
        Method setter = InaccessibleSetterValue.class.getMethod("setFluentValue", value.getClass());
        BiFunction<Object, Object, Object> thunk = returningAccessor(setter);
        BiConsumer<Object, Object> accessor = (instance, v) -> thunk.apply(instance, v);
        SettableObjectProperty property = new SettableObjectProperty(
                property(newVanillaJSONMapper(), bean.getClass(), "fluentValue"), accessor, thunk,
                InaccessibleSetterValue.class, setter.getParameterTypes()[0], false);

        // the same through the wrapper that fluent setters on regular beans use
        assertThrows(IllegalAccessError.class, () -> accessor.accept(bean, value));
        JsonMappingException e = assertThrows(JsonMappingException.class, () -> property.set(bean, value));
        assertEquals(IllegalAccessError.class, e.getCause().getClass());
        assertEquals(0, bean.calls);
    }

    private SettableBeanProperty property(ObjectMapper mapper, Class<?> beanType, String name) throws Exception {
        DefaultDeserializationContext context = ((DefaultDeserializationContext) mapper.getDeserializationContext())
                .createInstance(mapper.getDeserializationConfig(), null, null);
        BeanDeserializerBase deserializer = (BeanDeserializerBase) context.findRootValueDeserializer(
                mapper.constructType(beanType));
        return deserializer.findProperty(name);
    }

    @SuppressWarnings("unchecked")
    private BiConsumer<Object, Object> voidAccessor(Method setter) throws Throwable {
        MethodHandle handle = MethodHandles.lookup().unreflect(setter);
        return (BiConsumer<Object, Object>) LambdaMetafactory.metafactory(
                MethodHandles.lookup(), "accept", MethodType.methodType(BiConsumer.class),
                MethodType.methodType(void.class, Object.class, Object.class), handle, handle.type())
                .getTarget().invoke();
    }

    @SuppressWarnings("unchecked")
    private BiFunction<Object, Object, Object> returningAccessor(Method setter) throws Throwable {
        MethodHandle handle = MethodHandles.lookup().unreflect(setter);
        return (BiFunction<Object, Object, Object>) LambdaMetafactory.metafactory(
                MethodHandles.lookup(), "apply", MethodType.methodType(BiFunction.class),
                MethodType.methodType(Object.class, Object.class, Object.class), handle, handle.type())
                .getTarget().invoke();
    }
}
