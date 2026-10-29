package com.fasterxml.jackson.module.blackbird.deser;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;
import com.fasterxml.jackson.module.blackbird.deser.access.InaccessibleSetterValue;

import static org.junit.jupiter.api.Assertions.*;

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
    public void testVoidAccessorAccessFailureFallsBack() throws Throwable {
        InaccessibleSetterValue bean = new InaccessibleSetterValue();
        Object value = InaccessibleSetterValue.newValue();
        Method setter = InaccessibleSetterValue.class.getMethod("setVoidValue", value.getClass());
        BiConsumer<Object, Object> accessor = voidAccessor(setter);
        SettableObjectProperty property = new SettableObjectProperty(
                property(newVanillaJSONMapper(), bean.getClass(), "voidValue"), accessor, null,
                InaccessibleSetterValue.class);

        // The generated accessor cannot cast to the package-private parameter type.
        assertThrows(IllegalAccessError.class, () -> accessor.accept(bean, value));
        assertEquals(0, bean.calls);
        property.set(bean, value);
        assertEquals(1, bean.calls);
        assertSame(value, bean.value);
    }

    @Test
    public void testFluentAccessorAccessFailureFallsBack() throws Throwable {
        InaccessibleSetterValue bean = new InaccessibleSetterValue();
        Object value = InaccessibleSetterValue.newValue();
        Method setter = InaccessibleSetterValue.class.getMethod("setFluentValue", value.getClass());
        BiFunction<Object, Object, Object> thunk = returningAccessor(setter);
        BiConsumer<Object, Object> accessor = (instance, v) -> BBDeserializerModifier.trampoline(thunk, instance, v);
        SettableObjectProperty property = new SettableObjectProperty(
                property(newVanillaJSONMapper(), bean.getClass(), "fluentValue"), accessor, thunk,
                InaccessibleSetterValue.class);

        // With hidden lambda frames, the error's top frame is the trampoline.
        assertThrows(IllegalAccessError.class, () -> accessor.accept(bean, value));
        assertEquals(0, bean.calls);
        property.set(bean, value);
        assertEquals(1, bean.calls);
        assertSame(value, bean.value);
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
