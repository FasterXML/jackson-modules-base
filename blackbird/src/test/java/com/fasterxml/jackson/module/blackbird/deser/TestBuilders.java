package com.fasterxml.jackson.module.blackbird.deser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.impl.MethodProperty;
import com.fasterxml.jackson.databind.deser.impl.NullsConstantProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.util.ClassUtil;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;
import com.fasterxml.jackson.module.blackbird.deser.otherpackage.HiddenSetterBuilderBase;
import com.fasterxml.jackson.module.blackbird.deser.otherpackage.ReplacingBuilderBase;
import com.fasterxml.jackson.module.blackbird.deser.otherpackage.ReplacingBuilt;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    // The same, for nulls buffered before a creator property, which databind
    // passes to `set()`
    static final class CreatorSkipNullsBuilder
    {
        String str = "default";
        List<String> list = Arrays.asList("keep");
        int i = 7;
        long l = 8L;
        boolean b = true;

        @JsonCreator
        CreatorSkipNullsBuilder(@JsonProperty("id") String id) { }

        @JsonSetter(nulls=Nulls.SKIP)
        public CreatorSkipNullsBuilder withStr(String v) {
            str = v;
            return this;
        }

        @JsonSetter(nulls=Nulls.SKIP)
        public CreatorSkipNullsBuilder withList(List<String> v) {
            list = v;
            return this;
        }

        @JsonSetter(nulls=Nulls.SKIP)
        public CreatorSkipNullsBuilder withI(int v) {
            i = v;
            return this;
        }

        @JsonSetter(nulls=Nulls.SKIP)
        public CreatorSkipNullsBuilder withL(long v) {
            l = v;
            return this;
        }

        @JsonSetter(nulls=Nulls.SKIP)
        public CreatorSkipNullsBuilder withB(boolean v) {
            b = v;
            return this;
        }

        public CreatorSkipNullsBuilt build() {
            return new CreatorSkipNullsBuilt(str, list, i, l, b);
        }
    }

    @JsonDeserialize(builder=CreatorSkipNullsBuilder.class)
    static class CreatorSkipNullsBuilt extends Values {
        CreatorSkipNullsBuilt(String str, List<String> list, int i, long l, boolean b) {
            super(str, list, i, l, b, 0);
        }
    }

    // A failing setter should be reported the same way with and without Blackbird
    static Supplier<Throwable> toThrow;
    static int setterCalls;

    static final class ThrowingBuilder
    {
        public ThrowingBuilder withStr(String v) throws Exception {
            return fail();
        }

        public ThrowingBuilder withList(List<String> v) throws Exception {
            return fail();
        }

        public ThrowingBuilder withI(int v) throws Exception {
            return fail();
        }

        public ThrowingBuilder withL(long v) throws Exception {
            return fail();
        }

        public ThrowingBuilder withB(boolean v) throws Exception {
            return fail();
        }

        public void withV(String v) throws Exception {
            fail();
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

    // With a creator property, databind buffers the properties before it and calls
    // `set()` instead of `setAndReturn()` for them
    static final class CreatorThrowingBuilder
    {
        @JsonCreator
        CreatorThrowingBuilder(@JsonProperty("id") String id) { }

        public CreatorThrowingBuilder withStr(String v) throws Exception {
            ThrowingBuilder.fail();
            return this;
        }

        public CreatorThrowingBuilder withList(List<String> v) throws Exception {
            ThrowingBuilder.fail();
            return this;
        }

        public CreatorThrowingBuilder withI(int v) throws Exception {
            ThrowingBuilder.fail();
            return this;
        }

        public CreatorThrowingBuilder withL(long v) throws Exception {
            ThrowingBuilder.fail();
            return this;
        }

        public CreatorThrowingBuilder withB(boolean v) throws Exception {
            ThrowingBuilder.fail();
            return this;
        }

        public CreatorThrowingBuilt build() {
            return new CreatorThrowingBuilt();
        }
    }

    @JsonDeserialize(builder=CreatorThrowingBuilder.class)
    static class CreatorThrowingBuilt { }

    // Fails on the first call only, so a retry would succeed
    static Supplier<Throwable> flakyFailure;
    static int flakyCalls;

    static final class FlakyBuilder
    {
        String str;

        public FlakyBuilder withStr(String v) {
            if (flakyCalls++ == 0) {
                Throwable t = flakyFailure.get();
                if (t instanceof Error) {
                    throw (Error) t;
                }
                throw (RuntimeException) t;
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

    // The builder is a hidden subclass whose setter fails after doing its work. Its
    // frames are left out of stack traces, as the generated accessor's are.
    static Class<?> hiddenBuilderClass;
    static int hiddenCalls;

    static class HideableBuilder
    {
        @JsonCreator
        public static HideableBuilder create() throws Exception {
            return (HideableBuilder) hiddenBuilderClass.getConstructor().newInstance();
        }

        public HideableBuilder withStr(String v) {
            return this;
        }

        public HideableBuilt build() {
            return new HideableBuilt();
        }
    }

    public static class HiddenBuilder extends HideableBuilder
    {
        public HiddenBuilder() { }

        @Override
        public HideableBuilder withStr(String v) {
            hiddenCalls++;
            throw new SecurityException("thrown by hidden setter");
        }
    }

    @JsonDeserialize(builder=HideableBuilder.class)
    static class HideableBuilt { }

    // Inherits protected `withX`, whose generated setter only takes this class, but
    // `withFirst` replaces the builder with an instance of the base class
    public static final class ReplacingBuilder extends ReplacingBuilderBase { }

    // `withFirst` returns something that isn't a builder at all
    static final class UnrelatedBuilder
    {
        public Object withFirst(int v) {
            return "not a builder";
        }

        public UnrelatedBuilder withX(int v) { return this; }
        public UnrelatedBuilder withL(long v) { return this; }
        public UnrelatedBuilder withB(boolean v) { return this; }
        public UnrelatedBuilder withS(String v) { return this; }
        public UnrelatedBuilder withList(List<String> v) { return this; }

        public UnrelatedBuilt build() {
            return new UnrelatedBuilt();
        }
    }

    @JsonDeserialize(builder=UnrelatedBuilder.class)
    static class UnrelatedBuilt { }

    static final class NullCreatorBuilder
    {
        @JsonCreator
        public static NullCreatorBuilder create() {
            return null;
        }

        public NullCreatorBuilder withX(int v) { return this; }
        public NullCreatorBuilder withL(long v) { return this; }
        public NullCreatorBuilder withB(boolean v) { return this; }
        public NullCreatorBuilder withS(String v) { return this; }
        public NullCreatorBuilder withList(List<String> v) { return this; }

        public NullCreatorBuilt build() {
            return new NullCreatorBuilt();
        }
    }

    @JsonDeserialize(builder=NullCreatorBuilder.class)
    static class NullCreatorBuilt { }

    // Inherits a package-private setter from another package, which the generated
    // setter can't reach (`unreflect` refuses it) but reflection can
    public static final class HiddenSetterBuilder extends HiddenSetterBuilderBase
    {
        public HiddenSetterBuilt build() {
            return new HiddenSetterBuilt(y);
        }
    }

    @JsonDeserialize(builder=HiddenSetterBuilder.class)
    static class HiddenSetterBuilt
    {
        final int y;

        HiddenSetterBuilt(int y) {
            this.y = y;
        }
    }

    // Reflection can't call the protected setter without CAN_OVERRIDE_ACCESS_MODIFIERS
    static int protectedCalls;

    public static final class ProtectedSetterBuilder
    {
        int value;

        @JsonProperty("value")
        protected ProtectedSetterBuilder withValue(int v) {
            protectedCalls++;
            value = v;
            return this;
        }

        public ProtectedSetterBuilt build() {
            return new ProtectedSetterBuilt(value);
        }
    }

    // Public, so Jackson only makes it accessible with OVERRIDE_PUBLIC_ACCESS_MODIFIERS;
    // otherwise reflection applies Java's access rules (and modules), as must Blackbird,
    // which a public class in an exported package passes
    public static final class PublicSetterBuilder
    {
        int value;
        int reflectiveCalls;

        public PublicSetterBuilder withValue(int v) {
            reflectiveCalls += reflectiveCall();
            value = v;
            return this;
        }

        public PublicSetterBuilt build() {
            return new PublicSetterBuilt(value, reflectiveCalls);
        }
    }

    @JsonDeserialize(builder=PublicSetterBuilder.class)
    public static final class PublicSetterBuilt
    {
        public final int value;
        public final int reflectiveCalls;

        PublicSetterBuilt(int value, int reflectiveCalls) {
            this.value = value;
            this.reflectiveCalls = reflectiveCalls;
        }
    }

    @JsonDeserialize(builder=ProtectedSetterBuilder.class)
    public static final class ProtectedSetterBuilt
    {
        public final int value;

        ProtectedSetterBuilt(int value) {
            this.value = value;
        }
    }

    // As below, but the builder is the result; databind only finds its config
    @JsonDeserialize(builder=NoBuildMethodBuilder.class)
    @JsonPOJOBuilder(buildMethodName="")
    public static final class NoBuildMethodBuilder
    {
        public int value;

        @JsonCreator
        public NoBuildMethodBuilder(@JsonProperty("id") int id) { }

        @JsonProperty("value")
        protected NoBuildMethodBuilder withValue(int v) {
            protectedCalls++;
            value = v;
            return this;
        }
    }

    // As above, but properties before the creator property go through `set()`
    public static final class ProtectedCreatorSetterBuilder
    {
        int value;

        @JsonCreator
        public ProtectedCreatorSetterBuilder(@JsonProperty("id") int id) { }

        @JsonProperty("value")
        protected ProtectedCreatorSetterBuilder withValue(int v) {
            protectedCalls++;
            value = v;
            return this;
        }

        public ProtectedCreatorSetterBuilt build() {
            return new ProtectedCreatorSetterBuilt(value);
        }
    }

    @JsonDeserialize(builder=ProtectedCreatorSetterBuilder.class)
    public static final class ProtectedCreatorSetterBuilt
    {
        public final int value;

        ProtectedCreatorSetterBuilt(int value) {
            this.value = value;
        }
    }

    // Its creator gives a `String`, not a `Child`, which databind accepts
    static class Child
    {
        public int foo;

        @JsonCreator
        public static Object create() {
            return "not a Child";
        }
    }

    static final class ChildBuilder
    {
        public ChildBuilder withChild(Child c) {
            return this;
        }

        public ChildBuilt build() {
            return new ChildBuilt();
        }
    }

    @JsonDeserialize(builder=ChildBuilder.class)
    static class ChildBuilt { }

    // Overrides `getStackTrace`, which `MethodProperty` never calls
    static Throwable traceFailure;

    @SuppressWarnings("serial")
    static class TraceFailingException extends SecurityException
    {
        TraceFailingException() {
            super("thrown by setter");
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            if (traceFailure instanceof Error) {
                throw (Error) traceFailure;
            }
            throw (RuntimeException) traceFailure;
        }
    }

    static final class TraceFailingBuilder
    {
        public TraceFailingBuilder withX(int v) {
            throw new TraceFailingException();
        }

        public TraceFailingBuilt build() {
            return new TraceFailingBuilt();
        }
    }

    @JsonDeserialize(builder=TraceFailingBuilder.class)
    static class TraceFailingBuilt { }

    static final class NullTextBuilder
    {
        String skipped = "default";
        String plain = "default";
        int calls;

        @JsonSetter(nulls=Nulls.SKIP)
        public NullTextBuilder withSkipped(String v) {
            calls++;
            skipped = v;
            return this;
        }

        public NullTextBuilder withPlain(String v) {
            calls++;
            plain = v;
            return this;
        }

        public NullTextBuilt build() {
            return new NullTextBuilt(skipped + "," + plain + "," + calls);
        }
    }

    @JsonDeserialize(builder=NullTextBuilder.class)
    static class NullTextBuilt
    {
        final String result;

        NullTextBuilt(String result) {
            this.result = result;
        }
    }

    // An unknown type id gives null, which plain Jackson passes to the setter even
    // with `Nulls.SKIP`
    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME, defaultImpl=Void.class)
    static class Item {
        public String name;
    }

    static int itemCalls;

    static final class ItemBuilder
    {
        @JsonSetter(nulls=Nulls.SKIP)
        public ItemBuilder withFluent(Item i) {
            itemCalls++;
            return this;
        }

        @JsonSetter(nulls=Nulls.SKIP)
        public void withVoid(Item i) {
            itemCalls++;
        }

        public ItemBuilt build() {
            return new ItemBuilt();
        }
    }

    @JsonDeserialize(builder=ItemBuilder.class)
    static class ItemBuilt { }

    // Setters that return null keep the current builder, for every property type
    static final class NullReturningAllBuilder
    {
        long l;
        boolean b;
        List<String> list;

        public NullReturningAllBuilder withL(long v) { l = v; return null; }
        public NullReturningAllBuilder withB(boolean v) { b = v; return null; }
        public NullReturningAllBuilder withList(List<String> v) { list = v; return null; }

        public NullReturningAllBuilt build() { return new NullReturningAllBuilt(l, b, list); }
    }

    @JsonDeserialize(builder=NullReturningAllBuilder.class)
    static class NullReturningAllBuilt
    {
        final long l;
        final boolean b;
        final List<String> list;

        NullReturningAllBuilt(long l, boolean b, List<String> list) {
            this.l = l;
            this.b = b;
            this.list = list;
        }
    }

    // The null handling `deserializeSetAndReturn` does for an Object setter, as
    // `MethodProperty` does
    static final class NullsBuilder
    {
        List<String> empty = Arrays.asList("unset");
        List<String> skip = Arrays.asList("keep");
        int calls;

        @JsonSetter(nulls=Nulls.AS_EMPTY)
        public NullsBuilder withEmpty(List<String> v) { calls++; empty = v; return this; }

        @JsonSetter(nulls=Nulls.SKIP)
        public NullsBuilder withSkip(List<String> v) { calls++; skip = v; return this; }

        public NullsBuilt build() { return new NullsBuilt(this); }
    }

    @JsonDeserialize(builder=NullsBuilder.class)
    static class NullsBuilt
    {
        final String result;

        NullsBuilt(NullsBuilder b) {
            result = "empty=" + b.empty + " skip=" + b.skip + " calls=" + b.calls;
        }
    }

    // A public setter of a non-public builder, which Jackson makes accessible
    static final class PackagePrivateBuilder
    {
        public PackagePrivateBuilder withValue(int v) { return this; }
    }

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

    // through `MethodProperty`, or Blackbird's own reflective call for what its
    // generated setters can't take
    static int reflectiveCall() {
        for (StackTraceElement elem : new Throwable().getStackTrace()) {
            if (elem.getClassName().equals(MethodProperty.class.getName())
                    || elem.getMethodName().equals("_reflectiveSetAndReturn")) {
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

    // Whether the setter creates the throwable or saved it earlier, it may already
    // have done its work, so it must not run again. That includes throwables with no
    // stack trace, as with `-XX:-StackTraceInThrowable`.
    @Test
    public void testSetterFailureIsNotRetried() throws Exception
    {
        SecurityException savedSecurity = new SecurityException("saved");
        IllegalAccessError savedAccess = new IllegalAccessError("saved");
        List<Supplier<Throwable>> failures = Arrays.asList(
                () -> new SecurityException("thrown by setter"),
                () -> new IllegalAccessError("thrown by setter"),
                () -> savedSecurity,
                () -> savedAccess,
                () -> _withoutStackTrace(new SecurityException("stackless")),
                () -> _withoutStackTrace(new IllegalAccessError("stackless")));
        for (Supplier<Throwable> failure : failures) {
            flakyFailure = failure;
            flakyCalls = 0;
            assertThrows(JsonMappingException.class,
                    () -> MAPPER.readValue(a2q("{'str':'foo'}"), FlakyBuilt.class));
            assertEquals(1, flakyCalls, String.valueOf(failure.get()));
        }
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
    public void testSkipNullsBeforeCreatorProperty() throws Exception
    {
        String json = a2q("{'str':null,'list':null,'i':null,'l':null,'b':null,'id':'x'}");
        Values expected = newVanillaJSONMapper().readValue(json, CreatorSkipNullsBuilt.class);
        Values v = MAPPER.readValue(json, CreatorSkipNullsBuilt.class);
        assertEquals("default", expected.str);
        assertEquals(7, expected.i);
        assertEquals(expected.str, v.str);
        assertEquals(expected.list, v.list);
        assertEquals(expected.i, v.i);
        assertEquals(expected.l, v.l);
        assertEquals(expected.b, v.b);
    }

    @Test
    public void testThrowingSetterMatchesVanilla() throws Exception
    {
        SecurityException savedSecurity = new SecurityException("saved");
        IllegalAccessError savedAccess = new IllegalAccessError("saved");
        List<Supplier<Throwable>> failures = Arrays.asList(
                () -> new IllegalStateException("unchecked"),
                () -> new IllegalStateException("outer", new IllegalArgumentException("root")),
                () -> new IOException("bad value"),
                () -> new Exception("checked"),
                () -> new AssertionError("error"),
                () -> new SecurityException("thrown by setter"),
                () -> new IllegalAccessError("thrown by setter"),
                () -> savedSecurity,
                () -> savedAccess);
        // void setters still go through the delegate
        String voidJson = a2q("{'v':'foo'}");
        for (boolean wrap : new boolean[] { true, false }) {
            ObjectMapper vanilla = JsonMapper.builder()
                    .configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap).build();
            ObjectMapper blackbird = mapperBuilder()
                    .configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap).build();
            for (Supplier<Throwable> failure : failures) {
                for (String json : new String[] { a2q("{'str':'foo'}"), a2q("{'list':['a']}"),
                        a2q("{'i':42}"), a2q("{'l':42}"), a2q("{'b':true}"), voidJson }) {
                    toThrow = failure;
                    setterCalls = 0;
                    Throwable expected = assertThrows(Throwable.class,
                            () -> vanilla.readValue(json, ThrowingBuilt.class));
                    Throwable actual = assertThrows(Throwable.class,
                            () -> blackbird.readValue(json, ThrowingBuilt.class));
                    String desc = "wrap=" + wrap + ", " + json + ", " + expected;
                    assertEquals(2, setterCalls, desc);
                    _assertSameFailure(desc, expected, actual);
                    if (json.equals(voidJson)) {
                        continue;
                    }
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

    // The same, for properties buffered before a creator property, in either order
    @Test
    public void testThrowingBufferedSetterMatchesVanilla() throws Exception
    {
        List<Supplier<Throwable>> failures = Arrays.asList(
                () -> new IllegalStateException("unchecked"),
                () -> new IOException("bad value"),
                () -> new Exception("checked"),
                () -> new AssertionError("error"),
                () -> new SecurityException("thrown by setter"),
                () -> new IllegalAccessError("thrown by setter"));
        for (boolean wrap : new boolean[] { true, false }) {
            ObjectMapper vanilla = JsonMapper.builder()
                    .configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap).build();
            ObjectMapper blackbird = mapperBuilder()
                    .configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap).build();
            for (Supplier<Throwable> failure : failures) {
                for (String prop : new String[] { "'str':'foo'", "'list':['a']",
                        "'i':42", "'l':42", "'b':true" }) {
                    for (String json : new String[] { a2q("{" + prop + ",'id':'a'}"),
                            a2q("{'id':'a'," + prop + "}") }) {
                        toThrow = failure;
                        setterCalls = 0;
                        Throwable expected = assertThrows(Throwable.class,
                                () -> vanilla.readValue(json, CreatorThrowingBuilt.class));
                        Throwable actual = assertThrows(Throwable.class,
                                () -> blackbird.readValue(json, CreatorThrowingBuilt.class));
                        String desc = "wrap=" + wrap + ", " + json + ", " + expected;
                        assertEquals(2, setterCalls, desc);
                        _assertSameFailure(desc, expected, actual);
                    }
                }
            }
        }
    }

    // A setter in a hidden class looks like the generated accessor in a stack trace
    // (both are left out), but it must still run only once
    @Test
    public void testHiddenClassSetterFailureIsNotRetried() throws Exception
    {
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
        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream(
                "/" + HiddenBuilder.class.getName().replace('.', '/') + ".class")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            bytes = out.toByteArray();
        }
        hiddenBuilderClass = ((MethodHandles.Lookup) define.invoke(MethodHandles.lookup(), bytes, true, noOptions))
                .lookupClass();
        assertTrue(_findProperty(MAPPER, HideableBuilt.class, "str") instanceof OptimizedSettableBeanProperty);

        for (ObjectMapper mapper : new ObjectMapper[] { newVanillaJSONMapper(), MAPPER }) {
            hiddenCalls = 0;
            assertThrows(JsonMappingException.class,
                    () -> mapper.readValue(a2q("{'str':'foo'}"), HideableBuilt.class));
            assertEquals(1, hiddenCalls);
        }
    }

    @Test
    public void testReplacedBuilderOfBaseClass() throws Exception
    {
        String json = a2q("{'first':1,'x':2}");
        ReplacingBuilt expected = newVanillaJSONMapper().readValue(json, ReplacingBuilt.class);
        ReplacingBuilt actual = MAPPER.readValue(json, ReplacingBuilt.class);
        assertEquals(1, expected.first);
        assertEquals(2, expected.x);
        assertEquals(expected.first, actual.first);
        assertEquals(expected.x, actual.x);
    }

    // A builder that isn't an instance of the setter's class (or is null) must fail
    // as reflection does, not as a setter failure from the generated cast
    @Test
    public void testUnusableBuilderInstanceMatchesVanilla() throws Exception
    {
        assertTrue(_findProperty(MAPPER, UnrelatedBuilt.class, "x") instanceof OptimizedSettableBeanProperty);
        assertTrue(_findProperty(MAPPER, NullCreatorBuilt.class, "x") instanceof OptimizedSettableBeanProperty);
        for (boolean wrap : new boolean[] { true, false }) {
            ObjectMapper vanilla = JsonMapper.builder()
                    .configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap).build();
            ObjectMapper blackbird = mapperBuilder()
                    .configure(DeserializationFeature.WRAP_EXCEPTIONS, wrap).build();
            for (String value : new String[] { "'x':2", "'l':2", "'b':true", "'s':'a'", "'list':['a']" }) {
                // the unrelated builder comes from `withFirst`, the null one from its creator
                for (String json : new String[] { a2q("{'first':1," + value + "}"), a2q("{" + value + "}") }) {
                    Class<?> type = json.contains("first") ? UnrelatedBuilt.class : NullCreatorBuilt.class;
                    Throwable expected = assertThrows(Throwable.class, () -> vanilla.readValue(json, type));
                    Throwable actual = assertThrows(Throwable.class, () -> blackbird.readValue(json, type));
                    String desc = "wrap=" + wrap + ", " + type.getSimpleName() + ", " + json + ", " + expected;
                    // and not an unknown property, or some other failure before the setter
                    assertFalse(expected instanceof com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException,
                            desc);
                    _assertSameFailure(desc, expected, actual);
                }
            }
        }
    }

    // A failure of the generated accessor itself (simulated by the anonymous classes)
    // is reported like the setter's, without trying the setter again through
    // reflection, and with the JSON location where there is one
    @Test
    public void testAccessorFailureIsNotRetried() throws Exception
    {
        ObjectMapper vanilla = newVanillaJSONMapper();
        BeanDeserializerBase deser = (BeanDeserializerBase) ((DefaultDeserializationContext) vanilla.getDeserializationContext())
                .createInstance(vanilla.getDeserializationConfig(), null, null)
                .findRootValueDeserializer(vanilla.constructType(ThrowingBuilt.class));
        IllegalAccessError failure = new IllegalAccessError("simulated");
        SettableStringProperty prop = new SettableStringProperty(deser.findProperty("str"),
                new BiConsumer<Object, String>() {
                    @Override
                    public void accept(Object bean, String v) {
                        throw failure;
                    }
                },
                new BiFunction<Object, Object, Object>() {
                    @Override
                    public Object apply(Object bean, Object v) {
                        throw failure;
                    }
                },
                ThrowingBuilder.class, true);
        toThrow = () -> new IllegalStateException("thrown by setter");
        setterCalls = 0;

        JsonMappingException e = assertThrows(JsonMappingException.class,
                () -> prop.setAndReturn(new ThrowingBuilder(), "foo"));
        assertSame(failure, e.getCause());
        try (JsonParser p = vanilla.createParser(a2q("{'str':'foo'}"))) {
            p.nextToken();
            p.nextToken();
            p.nextToken();
            DefaultDeserializationContext ctxt = ((DefaultDeserializationContext) vanilla.getDeserializationContext())
                    .createInstance(vanilla.getDeserializationConfig(), p, null);
            e = assertThrows(JsonMappingException.class,
                    () -> prop.deserializeSetAndReturn(p, ctxt, new ThrowingBuilder()));
            assertSame(failure, e.getCause());
            assertNotNull(e.getLocation());
        }
        assertEquals(0, setterCalls);
    }

    @Test
    public void testInheritedPackagePrivateSetter() throws Exception
    {
        assertEquals(3, newVanillaJSONMapper().readValue(a2q("{'y':3}"), HiddenSetterBuilt.class).y);
        assertEquals(3, MAPPER.readValue(a2q("{'y':3}"), HiddenSetterBuilt.class).y);
    }

    // As `deserializeSetAndReturn` does, `setAndReturn` leaves a builder the generated
    // setter can't take to reflection, and skips nulls
    @Test
    public void testSetAndReturnWithUnusableBuilder() throws Exception
    {
        ObjectMapper vanilla = newVanillaJSONMapper();
        Object[][] cases = { { "i", 1 }, { "l", 1L }, { "b", true }, { "str", "x" }, { "list", Arrays.asList("x") } };
        for (Object[] c : cases) {
            String name = (String) c[0];
            for (Object instance : new Object[] { "not a builder", null }) {
                String desc = name + " on " + instance;
                Throwable expected = assertThrows(Throwable.class,
                        () -> _findProperty(vanilla, name).setAndReturn(instance, c[1]), desc);
                Throwable actual = assertThrows(Throwable.class,
                        () -> _findProperty(MAPPER, name).setAndReturn(instance, c[1]), desc);
                _assertSameFailure(desc + ", " + expected, expected, actual);
            }
        }
        for (String name : new String[] { "str", "list" }) {
            SettableBeanProperty prop = _findProperty(MAPPER, SkipNullsBuilt.class, name);
            assertTrue(prop instanceof OptimizedSettableBeanProperty, name);
            SkipNullsBuilder builder = new SkipNullsBuilder();
            assertSame(builder, prop.setAndReturn(builder, null));
            assertEquals("default", builder.str);
            assertEquals(Arrays.asList("keep"), builder.list);
        }
    }

    @Test
    public void testNullReturningBuilderForAllTypes() throws Exception
    {
        for (String name : new String[] { "l", "b", "list" }) {
            assertTrue(_findProperty(MAPPER, NullReturningAllBuilt.class, name) instanceof OptimizedSettableBeanProperty,
                    name);
        }
        NullReturningAllBuilt v = MAPPER.readValue(a2q("{'l':7,'b':true,'list':['x']}"), NullReturningAllBuilt.class);
        assertEquals(7L, v.l);
        assertTrue(v.b);
        assertEquals(Arrays.asList("x"), v.list);
        // and through setAndReturn
        NullReturningAllBuilder builder = new NullReturningAllBuilder();
        assertSame(builder, _findProperty(MAPPER, NullReturningAllBuilt.class, "l").setAndReturn(builder, 3L));
        assertSame(builder, _findProperty(MAPPER, NullReturningAllBuilt.class, "b").setAndReturn(builder, true));
        assertSame(builder, _findProperty(MAPPER, NullReturningAllBuilt.class, "list")
                .setAndReturn(builder, Collections.singletonList("y")));
    }

    @Test
    public void testBuilderNullHandlingMatchesVanilla() throws Exception
    {
        ObjectMapper vanilla = JsonMapper.builder()
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT).build();
        ObjectMapper blackbird = mapperBuilder()
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT).build();
        for (String name : new String[] { "empty", "skip" }) {
            assertTrue(_findProperty(blackbird, NullsBuilt.class, name) instanceof OptimizedSettableBeanProperty, name);
        }
        // explicit nulls, and empty Strings the deserializer turns into nulls
        for (String json : new String[] { a2q("{'empty':null,'skip':null}"), a2q("{'empty':'','skip':''}") }) {
            String expected = vanilla.readValue(json, NullsBuilt.class).result;
            assertEquals("empty=[] skip=[keep] calls=1", expected, json);
            assertEquals(expected, blackbird.readValue(json, NullsBuilt.class).result, json);
        }
    }

    // `setAndReturn` with a null primitive fails as reflection does, unless nulls are skipped
    @Test
    public void testSetAndReturnNullPrimitives() throws Exception
    {
        ObjectMapper vanilla = newVanillaJSONMapper();
        for (String name : new String[] { "i", "l", "b" }) {
            SettableBeanProperty expectedProperty = _findProperty(vanilla, name);
            SettableBeanProperty actualProperty = _findProperty(MAPPER, name);
            assertTrue(actualProperty instanceof OptimizedSettableBeanProperty, name);
            MutableBuilder expectedBuilder = new MutableBuilder();
            MutableBuilder actualBuilder = new MutableBuilder();
            JsonMappingException expected = assertThrows(JsonMappingException.class,
                    () -> expectedProperty.setAndReturn(expectedBuilder, null), name);
            JsonMappingException actual = assertThrows(JsonMappingException.class,
                    () -> actualProperty.setAndReturn(actualBuilder, null), name);
            assertEquals(expected.getOriginalMessage(), actual.getOriginalMessage(), name);
            assertEquals(expected.getCause().getClass(), actual.getCause().getClass(), name);
            assertEquals(0, actualBuilder.reflectiveCalls, name);

            SettableBeanProperty skipping = actualProperty.withNullProvider(NullsConstantProvider.skipper());
            assertTrue(skipping instanceof OptimizedSettableBeanProperty, name);
            assertSame(actualBuilder, skipping.setAndReturn(actualBuilder, null), name);
            assertEquals(0, actualBuilder.reflectiveCalls, name);
        }
    }

    // The rule the generated builder setters follow is the one Jackson uses to make
    // setters accessible, including leaving public JDK setters alone
    @SuppressWarnings("deprecation")
    @Test
    public void testIsMadeAccessibleMatchesJackson() throws Exception
    {
        for (boolean canOverride : new boolean[] { true, false }) {
            for (boolean overridePublic : new boolean[] { true, false }) {
                DeserializationConfig config = mapperBuilder()
                        .configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, canOverride)
                        .configure(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS, overridePublic)
                        .build().getDeserializationConfig();
                // fresh `Method` copies, as each lookup returns
                Method[] setters = {
                    javax.naming.CompositeName.class.getMethod("add", String.class),
                    PublicSetterBuilder.class.getMethod("withValue", int.class),
                    ProtectedSetterBuilder.class.getDeclaredMethod("withValue", int.class),
                    PackagePrivateBuilder.class.getMethod("withValue", int.class),
                };
                for (Method setter : setters) {
                    // as `BeanDeserializerBuilder` and `MethodProperty.fixAccess` do
                    if (config.canOverrideAccessModifiers()) {
                        ClassUtil.checkAndFixAccess(setter,
                                config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS));
                    }
                    assertEquals(setter.isAccessible(), BBDeserializerModifier.isMadeAccessible(config, setter),
                            "canOverride=" + canOverride + ", overridePublic=" + overridePublic + ", " + setter);
                }
            }
        }
    }

    @Test
    public void testAccessModifierOverridesMatchVanilla() throws Exception
    {
        String json = a2q("{'value':42}");
        for (boolean canOverride : new boolean[] { true, false }) {
            for (boolean overridePublic : new boolean[] { true, false }) {
                String desc = "canOverride=" + canOverride + ", overridePublic=" + overridePublic;
                ObjectMapper vanilla = JsonMapper.builder()
                        .configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, canOverride)
                        .configure(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS, overridePublic).build();
                ObjectMapper blackbird = mapperBuilder()
                        .configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, canOverride)
                        .configure(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS, overridePublic).build();

                // reflection can only call the protected setter if Jackson made it accessible
                if (canOverride) {
                    assertEquals(42, vanilla.readValue(json, ProtectedSetterBuilt.class).value, desc);
                    assertEquals(42, blackbird.readValue(json, ProtectedSetterBuilt.class).value, desc);
                } else {
                    protectedCalls = 0;
                    Throwable expected = assertThrows(Throwable.class,
                            () -> vanilla.readValue(json, ProtectedSetterBuilt.class), desc);
                    Throwable actual = assertThrows(Throwable.class,
                            () -> blackbird.readValue(json, ProtectedSetterBuilt.class), desc);
                    _assertSameFailure(desc + ", " + expected, expected, actual);
                    assertEquals(0, protectedCalls, desc);
                }

                // and the same for a property buffered before the creator property
                String bufferedJson = a2q("{'value':42,'id':1}");
                if (canOverride) {
                    assertEquals(42, blackbird.readValue(bufferedJson, ProtectedCreatorSetterBuilt.class).value, desc);
                } else {
                    protectedCalls = 0;
                    Throwable expected = assertThrows(Throwable.class,
                            () -> vanilla.readValue(bufferedJson, ProtectedCreatorSetterBuilt.class), desc);
                    Throwable actual = assertThrows(Throwable.class,
                            () -> blackbird.readValue(bufferedJson, ProtectedCreatorSetterBuilt.class), desc);
                    _assertSameFailure(desc + ", " + expected, expected, actual);
                    assertEquals(0, protectedCalls, desc);
                }

                // the public one always works here, so goes through the generated setter
                PublicSetterBuilt result = blackbird.readValue(json, PublicSetterBuilt.class);
                assertEquals(42, result.value, desc);
                assertEquals(0, result.reflectiveCalls, desc);

                // and a builder without a build method is still a builder
                protectedCalls = 0;
                if (canOverride) {
                    assertEquals(42, blackbird.readValue(bufferedJson, NoBuildMethodBuilder.class).value, desc);
                } else {
                    Throwable expected = assertThrows(Throwable.class,
                            () -> vanilla.readValue(bufferedJson, NoBuildMethodBuilder.class), desc);
                    Throwable actual = assertThrows(Throwable.class,
                            () -> blackbird.readValue(bufferedJson, NoBuildMethodBuilder.class), desc);
                    _assertSameFailure(desc + ", " + expected, expected, actual);
                    assertEquals(0, protectedCalls, desc);
                }
            }
        }
    }

    @Test
    public void testValueOfAnotherTypeMatchesVanilla() throws Exception
    {
        assertTrue(_findProperty(MAPPER, ChildBuilt.class, "child") instanceof OptimizedSettableBeanProperty);
        String json = a2q("{'child':{}}");
        Throwable expected = assertThrows(Throwable.class, () -> newVanillaJSONMapper().readValue(json, ChildBuilt.class));
        Throwable actual = assertThrows(Throwable.class, () -> MAPPER.readValue(json, ChildBuilt.class));
        _assertSameFailure(String.valueOf(expected), expected, actual);
    }

    @Test
    public void testFailingStackTraceKeepsSetterFailure() throws Exception
    {
        String json = a2q("{'x':1}");
        for (Throwable t : new Throwable[] { new IllegalStateException("no trace"), new AssertionError("no trace") }) {
            traceFailure = t;
            Throwable expected = assertThrows(Throwable.class,
                    () -> newVanillaJSONMapper().readValue(json, TraceFailingBuilt.class));
            Throwable actual = assertThrows(Throwable.class, () -> MAPPER.readValue(json, TraceFailingBuilt.class));
            assertEquals(TraceFailingException.class, actual.getCause().getClass(), String.valueOf(t));
            _assertSameFailure(t + ", " + expected, expected, actual);
        }
    }

    // `TextNode(null)` reads as a VALUE_STRING whose text is null
    @Test
    public void testNullTextMatchesVanilla() throws Exception
    {
        assertTrue(_findProperty(MAPPER, NullTextBuilt.class, "skipped") instanceof OptimizedSettableBeanProperty);
        ObjectNode tree = JsonNodeFactory.instance.objectNode();
        tree.set("skipped", new TextNode(null));
        tree.set("plain", new TextNode(null));
        String expected = newVanillaJSONMapper().treeToValue(tree, NullTextBuilt.class).result;
        assertEquals("default,null,1", expected);
        assertEquals(expected, MAPPER.treeToValue(tree, NullTextBuilt.class).result);
    }

    @Test
    public void testPolymorphicNullMatchesVanilla() throws Exception
    {
        assertTrue(_findProperty(MAPPER, ItemBuilt.class, "fluent") instanceof OptimizedSettableBeanProperty);
        assertTrue(_findProperty(MAPPER, ItemBuilt.class, "void") instanceof OptimizedSettableBeanProperty);
        for (String name : new String[] { "fluent", "void" }) {
            String json = a2q("{'" + name + "':{'@type':'Unknown'}}");
            itemCalls = 0;
            newVanillaJSONMapper().readValue(json, ItemBuilt.class);
            int expected = itemCalls;
            itemCalls = 0;
            MAPPER.readValue(json, ItemBuilt.class);
            assertEquals(expected, itemCalls, name);
        }
    }

    // `setAndReturn` can be given values its property's deserializer didn't produce,
    // such as an object id from a generator. Reflection widens some and rejects
    // others, and the generated setters must not cast or truncate them instead.
    @Test
    public void testSetAndReturnWithOtherValueTypes() throws Exception
    {
        Object[][] cases = {
            { "i", 'A' }, { "i", (short) 7 }, { "i", 4294967297L }, { "i", 3.9 }, { "i", "x" },
            { "l", 'A' }, { "l", 7 }, { "l", 3.9 },
            { "b", 1 }, { "b", "true" },
            { "str", 1 }, { "list", "x" },
        };
        ObjectMapper vanilla = newVanillaJSONMapper();
        for (Object[] c : cases) {
            String name = (String) c[0];
            assertTrue(_findProperty(MAPPER, name) instanceof OptimizedSettableBeanProperty, name);
            assertEquals(_setAndReturn(vanilla, name, c[1]), _setAndReturn(MAPPER, name, c[1]),
                    name + "=" + c[1] + " (" + c[1].getClass().getSimpleName() + ")");
        }
        // while values of the setter's own type still use the generated setters
        for (Object[] c : new Object[][] { { "i", 42 }, { "l", 42L }, { "b", true }, { "str", "x" },
                { "list", Arrays.asList("x") } }) {
            MutableBuilder b = (MutableBuilder) _findProperty(MAPPER, (String) c[0])
                    .setAndReturn(new MutableBuilder(), c[1]);
            assertEquals(0, b.reflectiveCalls, String.valueOf(c[0]));
        }
    }

    private static String _setAndReturn(ObjectMapper mapper, String name, Object value) throws Exception {
        try {
            MutableBuilder b = (MutableBuilder) _findProperty(mapper, name).setAndReturn(new MutableBuilder(), value);
            return "str=" + b.str + " list=" + b.list + " i=" + b.i + " l=" + b.l + " b=" + b.b;
        } catch (Exception e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    private static SettableBeanProperty _findProperty(ObjectMapper mapper, String name) throws Exception {
        return _findProperty(mapper, MutableBuilt.class, name);
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

    private static <T extends Throwable> T _withoutStackTrace(T t) {
        t.setStackTrace(new StackTraceElement[0]);
        return t;
    }

    private static void _assertSameFailure(String desc, Throwable expected, Throwable actual) {
        assertEquals(expected.getClass(), actual.getClass(), desc);
        if (expected instanceof JsonMappingException) {
            JsonMappingException e = (JsonMappingException) expected;
            JsonMappingException a = (JsonMappingException) actual;
            assertEquals(e.getOriginalMessage(), a.getOriginalMessage(), desc);
            assertEquals(e.getPathReference(), a.getPathReference(), desc);
            assertEquals(e.getLocation(), a.getLocation(), desc);
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
