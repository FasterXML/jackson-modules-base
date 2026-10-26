package com.fasterxml.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.impl.MethodProperty;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestBuilders extends BlackbirdTestBase
{
    // [Issue#22]:
    static final class ThingBuilder
    {
        private String foo;

        public ThingBuilder withFoo(String str) {
            foo = str;
            return this;
        }

        public Thing build() {
            return new Thing(foo);
        }
    }

    @JsonDeserialize(builder=ThingBuilder.class)
    static class Thing {
        final String foo;
    
        Thing(String foo) {
            this.foo = foo;
        }
    }

    // Builder setters should go through generated accessors, not reflection
    static final class MutableBuilder
    {
        String str;
        List<String> list;
        int i;
        long l;
        boolean b;
        int reflectiveCalls;

        public MutableBuilder withStr(String v) {
            reflectiveCalls += reflectiveCall();
            str = v;
            return this;
        }

        public MutableBuilder withList(List<String> v) {
            reflectiveCalls += reflectiveCall();
            list = v;
            return this;
        }

        public MutableBuilder withI(int v) {
            reflectiveCalls += reflectiveCall();
            i = v;
            return this;
        }

        public MutableBuilder withL(long v) {
            reflectiveCalls += reflectiveCall();
            l = v;
            return this;
        }

        public MutableBuilder withB(boolean v) {
            reflectiveCalls += reflectiveCall();
            b = v;
            return this;
        }

        public MutableBuilt build() {
            return new MutableBuilt(str, list, i, l, b, reflectiveCalls);
        }
    }

    @JsonDeserialize(builder=MutableBuilder.class)
    static class MutableBuilt extends Values {
        MutableBuilt(String str, List<String> list, int i, long l, boolean b, int reflectiveCalls) {
            super(str, list, i, l, b, reflectiveCalls);
        }
    }

    // Each setter returns a new builder, so the returned value must be used
    static final class CopyingBuilder
    {
        final String str;
        final List<String> list;
        final int i;
        final long l;
        final boolean b;
        final int reflectiveCalls;

        public CopyingBuilder() {
            this(null, null, 0, 0L, false, 0);
        }

        CopyingBuilder(String str, List<String> list, int i, long l, boolean b, int reflectiveCalls) {
            this.str = str;
            this.list = list;
            this.i = i;
            this.l = l;
            this.b = b;
            this.reflectiveCalls = reflectiveCalls;
        }

        public CopyingBuilder withStr(String v) {
            return new CopyingBuilder(v, list, i, l, b, reflectiveCalls + reflectiveCall());
        }

        public CopyingBuilder withList(List<String> v) {
            return new CopyingBuilder(str, v, i, l, b, reflectiveCalls + reflectiveCall());
        }

        public CopyingBuilder withI(int v) {
            return new CopyingBuilder(str, list, v, l, b, reflectiveCalls + reflectiveCall());
        }

        public CopyingBuilder withL(long v) {
            return new CopyingBuilder(str, list, i, v, b, reflectiveCalls + reflectiveCall());
        }

        public CopyingBuilder withB(boolean v) {
            return new CopyingBuilder(str, list, i, l, v, reflectiveCalls + reflectiveCall());
        }

        public CopyingBuilt build() {
            return new CopyingBuilt(str, list, i, l, b, reflectiveCalls);
        }
    }

    @JsonDeserialize(builder=CopyingBuilder.class)
    static class CopyingBuilt extends Values {
        CopyingBuilt(String str, List<String> list, int i, long l, boolean b, int reflectiveCalls) {
            super(str, list, i, l, b, reflectiveCalls);
        }
    }

    // Stock databind keeps the current builder when a setter returns null
    static final class NullReturningBuilder
    {
        String str;
        int i;

        public NullReturningBuilder withStr(String v) {
            str = v;
            return null;
        }

        public NullReturningBuilder withI(int v) {
            i = v;
            return null;
        }

        public NullReturningBuilt build() {
            return new NullReturningBuilt(str, i);
        }
    }

    @JsonDeserialize(builder=NullReturningBuilder.class)
    static class NullReturningBuilt extends Values {
        NullReturningBuilt(String str, int i) {
            super(str, null, i, 0L, false, 0);
        }
    }

    // Void setters keep going through the delegate
    static final class VoidSetterBuilder
    {
        String str;
        List<String> list;
        int i;
        long l;
        boolean b;

        public void withStr(String v) { str = v; }
        public void withList(List<String> v) { list = v; }
        public void withI(int v) { i = v; }
        public void withL(long v) { l = v; }
        public void withB(boolean v) { b = v; }

        public VoidSetterBuilt build() {
            return new VoidSetterBuilt(str, list, i, l, b);
        }
    }

    @JsonDeserialize(builder=VoidSetterBuilder.class)
    static class VoidSetterBuilt extends Values {
        VoidSetterBuilt(String str, List<String> list, int i, long l, boolean b) {
            super(str, list, i, l, b, 0);
        }
    }

    // Explicit nulls must be skipped, leaving the builder's defaults intact
    static final class SkipNullsBuilder
    {
        String str = "default";
        List<String> list = Arrays.asList("keep");

        @JsonSetter(nulls=Nulls.SKIP)
        public SkipNullsBuilder withStr(String v) {
            str = v;
            return this;
        }

        @JsonSetter(nulls=Nulls.SKIP)
        public SkipNullsBuilder withList(List<String> v) {
            list = v;
            return this;
        }

        public SkipNullsBuilt build() {
            return new SkipNullsBuilt(str, list);
        }
    }

    @JsonDeserialize(builder=SkipNullsBuilder.class)
    static class SkipNullsBuilt extends Values {
        SkipNullsBuilt(String str, List<String> list) {
            super(str, list, 0, 0L, false, 0);
        }
    }

    // A failing setter should be reported the same way with and without Blackbird.
    // The throwable is created inside the setter, as a real one would be.
    static Supplier<Throwable> toThrow;
    static int setterCalls;

    static final class ThrowingBuilder
    {
        public ThrowingBuilder withStr(String v) throws Exception {
            return fail();
        }

        public ThrowingBuilder withI(int v) throws Exception {
            return fail();
        }

        private static ThrowingBuilder fail() throws Exception {
            setterCalls++;
            Throwable t = toThrow.get();
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw (Exception) t;
        }

        public ThrowingBuilt build() {
            return new ThrowingBuilt();
        }
    }

    @JsonDeserialize(builder=ThrowingBuilder.class)
    static class ThrowingBuilt { }

    // Fails inside the setter on the first call only, so a retry would succeed
    static int flakyCalls;

    static final class FlakyBuilder
    {
        String str;

        public FlakyBuilder withStr(String v) {
            if (flakyCalls++ == 0) {
                throw new SecurityException("thrown by setter");
            }
            str = v;
            return this;
        }

        public FlakyBuilt build() {
            return new FlakyBuilt();
        }
    }

    @JsonDeserialize(builder=FlakyBuilder.class)
    static class FlakyBuilt { }

    static class Values {
        final String str;
        final List<String> list;
        final int i;
        final long l;
        final boolean b;
        final int reflectiveCalls;

        Values(String str, List<String> list, int i, long l, boolean b, int reflectiveCalls) {
            this.str = str;
            this.list = list;
            this.i = i;
            this.l = l;
            this.b = b;
            this.reflectiveCalls = reflectiveCalls;
        }
    }

    static int reflectiveCall() {
        for (StackTraceElement elem : new Throwable().getStackTrace()) {
            if (elem.getClassName().equals(MethodProperty.class.getName())) {
                return 1;
            }
        }
        return 0;
    }

    private static final String VALUES_JSON = a2q(
            "{'str':'foo','list':['a','b'],'i':123456,'l':12345678901,'b':true}");

    /*
    /**********************************************************
    /* Test methods, valid cases, non-deferred, no-mixins
    /**********************************************************
     */
    
    private final ObjectMapper MAPPER = newObjectMapper();
    
    @Test
    public void testSimpleBuilder() throws Exception
    {
        final Thing expected = new ThingBuilder().withFoo("bar").build();
        final Thing actual = MAPPER.readValue("{ \"foo\": \"bar\"}", Thing.class);
        
        assertNotNull(actual);
        assertNotNull(actual.foo);
        assertEquals(expected.foo, actual.foo);
    }

    @Test
    public void testMutableBuilderSkipsReflection() throws Exception
    {
        Values v = MAPPER.readValue(VALUES_JSON, MutableBuilt.class);
        _verifyValues(v);
        assertEquals(0, v.reflectiveCalls);
    }

    @Test
    public void testCopyingBuilderSkipsReflection() throws Exception
    {
        Values v = MAPPER.readValue(VALUES_JSON, CopyingBuilt.class);
        _verifyValues(v);
        assertEquals(0, v.reflectiveCalls);
    }

    // Sanity check that the reflection probe works at all
    @Test
    public void testVanillaBuilderUsesReflection() throws Exception
    {
        ObjectMapper vanilla = newVanillaJSONMapper();
        Values v = vanilla.readValue(VALUES_JSON, MutableBuilt.class);
        _verifyValues(v);
        assertEquals(5, v.reflectiveCalls);
        v = vanilla.readValue(VALUES_JSON, CopyingBuilt.class);
        _verifyValues(v);
        assertEquals(5, v.reflectiveCalls);
    }

    @Test
    public void testNullReturningBuilder() throws Exception
    {
        Values v = MAPPER.readValue(a2q("{'str':'foo','i':42}"), NullReturningBuilt.class);
        assertEquals("foo", v.str);
        assertEquals(42, v.i);
    }

    // An access failure of the generated accessor happens before the setter
    // runs; the reflective fallback must still return the new builder
    @Test
    public void testAccessFailureFallsBackToReflection() throws Exception
    {
        ObjectMapper vanilla = newVanillaJSONMapper();
        DefaultDeserializationContext ctxt = ((DefaultDeserializationContext) vanilla.getDeserializationContext())
                .createInstance(vanilla.getDeserializationConfig(), null, null);
        BeanDeserializerBase deser = (BeanDeserializerBase) ctxt.findRootValueDeserializer(
                vanilla.constructType(CopyingBuilt.class));
        SettableStringProperty prop = new SettableStringProperty(deser.findProperty("str"),
                (bean, v) -> { throw new IllegalAccessError("simulated"); },
                (bean, v) -> { throw new IllegalAccessError("simulated"); });

        CopyingBuilder result = (CopyingBuilder) prop.setAndReturn(new CopyingBuilder(), "foo");
        assertEquals("foo", result.str);
        assertEquals(1, result.reflectiveCalls);
    }

    @Test
    public void testSetterSecurityExceptionIsNotRetried() throws Exception
    {
        flakyCalls = 0;
        assertThrows(JsonMappingException.class,
                () -> MAPPER.readValue(a2q("{'str':'foo'}"), FlakyBuilt.class));
        assertEquals(1, flakyCalls);
    }

    @Test
    public void testVoidSetterBuilder() throws Exception
    {
        _verifyValues(MAPPER.readValue(VALUES_JSON, VoidSetterBuilt.class));
    }

    @Test
    public void testSkipNullsBuilder() throws Exception
    {
        Values v = MAPPER.readValue(a2q("{'str':null,'list':null}"), SkipNullsBuilt.class);
        assertEquals("default", v.str);
        assertEquals(Arrays.asList("keep"), v.list);
    }

    @Test
    public void testThrowingSetterMatchesVanilla() throws Exception
    {
        List<Supplier<Throwable>> failures = Arrays.asList(
                () -> new IllegalStateException("unchecked"),
                () -> new IllegalStateException("outer", new IllegalArgumentException("root")),
                () -> new IOException("bad value"),
                () -> new Exception("checked"),
                () -> new AssertionError("error"),
                () -> new SecurityException("thrown by setter"));
        for (boolean wrap : new boolean[] { true, false }) {
            ObjectMapper vanilla = JsonMapper.builder()
                    .configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap).build();
            ObjectMapper blackbird = mapperBuilder()
                    .configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap).build();
            for (Supplier<Throwable> failure : failures) {
                for (String json : new String[] { a2q("{'str':'foo'}"), a2q("{'i':42}") }) {
                    toThrow = failure;
                    setterCalls = 0;
                    Throwable expected = assertThrows(Throwable.class,
                            () -> vanilla.readValue(json, ThrowingBuilt.class));
                    Throwable actual = assertThrows(Throwable.class,
                            () -> blackbird.readValue(json, ThrowingBuilt.class));
                    String desc = "wrap=" + wrap + ", " + json + ", " + expected;
                    assertEquals(2, setterCalls, desc);
                    _assertSameFailure(desc, expected, actual);
                    // and the Blackbird failure came from the generated accessor
                    Throwable root = actual;
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    assertTrue(Arrays.stream(root.getStackTrace())
                            .noneMatch(e -> e.getClassName().equals(MethodProperty.class.getName())), desc);
                }
            }
        }
    }

    private static void _assertSameFailure(String desc, Throwable expected, Throwable actual) {
        assertEquals(expected.getClass(), actual.getClass(), desc);
        if (expected instanceof JsonMappingException) {
            JsonMappingException e = (JsonMappingException) expected;
            JsonMappingException a = (JsonMappingException) actual;
            assertEquals(e.getOriginalMessage(), a.getOriginalMessage(), desc);
            assertEquals(e.getPathReference(), a.getPathReference(), desc);
        } else {
            assertEquals(expected.getMessage(), actual.getMessage(), desc);
        }
        Throwable ec = expected.getCause();
        Throwable ac = actual.getCause();
        assertEquals((ec == null) ? null : ec.getClass(), (ac == null) ? null : ac.getClass(), desc);
        if (ec != null) {
            assertEquals(ec.getMessage(), ac.getMessage(), desc);
        }
    }

    private void _verifyValues(Values v) {
        assertEquals("foo", v.str);
        assertEquals(Arrays.asList("a", "b"), v.list);
        assertEquals(123456, v.i);
        assertEquals(12345678901L, v.l);
        assertTrue(v.b);
    }
}
