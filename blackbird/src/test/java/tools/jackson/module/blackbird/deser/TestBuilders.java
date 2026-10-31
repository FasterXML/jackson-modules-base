package tools.jackson.module.blackbird.deser;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.InjectableValues;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.deser.impl.MethodProperty;
import tools.jackson.databind.deser.impl.NullsConstantProvider;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;
import tools.jackson.module.blackbird.deser.otherpackage.HiddenSetterBuilderBase;
import tools.jackson.module.blackbird.deser.otherpackage.InjectedSetterBuilderBase;
import tools.jackson.module.blackbird.deser.otherpackage.ReplacingBuilderBase;
import tools.jackson.module.blackbird.deser.otherpackage.ReplacingBuilt;

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

    // Void setters use the generated accessor too, and keep the same builder
    static final class VoidSetterBuilder
    {
        String str;
        List<String> list;
        int i;
        long l;
        boolean b;
        int reflectiveCalls;

        public void withStr(String v) { str = v; reflectiveCalls += reflectiveCall(); }
        public void withList(List<String> v) { list = v; reflectiveCalls += reflectiveCall(); }
        public void withI(int v) { i = v; reflectiveCalls += reflectiveCall(); }
        public void withL(long v) { l = v; reflectiveCalls += reflectiveCall(); }
        public void withB(boolean v) { b = v; reflectiveCalls += reflectiveCall(); }

        public VoidSetterBuilt build() {
            return new VoidSetterBuilt(str, list, i, l, b, reflectiveCalls);
        }
    }

    @JsonDeserialize(builder=VoidSetterBuilder.class)
    static class VoidSetterBuilt extends Values {
        VoidSetterBuilt(String str, List<String> list, int i, long l, boolean b, int reflectiveCalls) {
            super(str, list, i, l, b, reflectiveCalls);
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
    public static final class InjectedSetterBuilder extends InjectedSetterBuilderBase
    {
        public InjectedSetterBuilt build() {
            return new InjectedSetterBuilt(tag);
        }
    }

    @JsonDeserialize(builder=InjectedSetterBuilder.class)
    static class InjectedSetterBuilt
    {
        final String tag;

        InjectedSetterBuilt(String tag) {
            this.tag = tag;
        }
    }

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

    // The same, buffered before a creator property, so databind calls `set()`
    static final class CreatorChildBuilder
    {
        @JsonCreator
        CreatorChildBuilder(@JsonProperty("id") int id) { }

        public CreatorChildBuilder withChild(Child c) {
            return this;
        }

        public CreatorChildBuilt build() {
            return new CreatorChildBuilt();
        }
    }

    @JsonDeserialize(builder=CreatorChildBuilder.class)
    static class CreatorChildBuilt { }

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

    // for calling `set()` and `setAndReturn()` directly
    private static final DeserializationContext CTXT = new JsonMapper()._deserializationContext();
    
    @Test
    public void testSimpleBuilder() throws Exception
    {
        final Thing expected = new ThingBuilder().withFoo("bar").build();
        final Thing actual = MAPPER.readValue("{ \"foo\": \"bar\"}", Thing.class);
        
        assertNotNull(actual);
        assertNotNull(actual.foo);
        assertEquals(expected.foo, actual.foo);
    }

    // Only the `List` property, which generated readers leave to its stock
    // `MethodProperty`, goes through reflection; the scalar ones are generated
    private static final int STOCK_PROPERTIES = 1;

    @Test
    public void testMutableBuilderSkipsReflection() throws Exception
    {
        Values v = MAPPER.readValue(VALUES_JSON, MutableBuilt.class);
        _verifyValues(v);
        assertEquals(STOCK_PROPERTIES, v.reflectiveCalls);
    }

    @Test
    public void testCopyingBuilderSkipsReflection() throws Exception
    {
        Values v = MAPPER.readValue(VALUES_JSON, CopyingBuilt.class);
        _verifyValues(v);
        assertEquals(STOCK_PROPERTIES, v.reflectiveCalls);
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
    // have done its work, so it must not run again
    @Test
    public void testSetterFailureIsNotRetried() throws Exception
    {
        SecurityException savedSecurity = new SecurityException("saved");
        IllegalAccessError savedAccess = new IllegalAccessError("saved");
        List<Supplier<Throwable>> failures = Arrays.asList(
                () -> new SecurityException("thrown by setter"),
                () -> new IllegalAccessError("thrown by setter"),
                () -> savedSecurity,
                () -> savedAccess);
        for (Supplier<Throwable> failure : failures) {
            flakyFailure = failure;
            flakyCalls = 0;
            assertThrows(Throwable.class,
                    () -> MAPPER.readValue(a2q("{'str':'foo'}"), FlakyBuilt.class));
            assertEquals(1, flakyCalls, String.valueOf(failure.get()));
        }
    }

    @Test
    public void testVoidSetterBuilder() throws Exception
    {
        Values v = MAPPER.readValue(VALUES_JSON, VoidSetterBuilt.class);
        _verifyValues(v);
        assertEquals(STOCK_PROPERTIES, v.reflectiveCalls);
        // as reflection would, with Jackson's own access rules
        assertEquals(5, newVanillaJSONMapper().readValue(VALUES_JSON, VoidSetterBuilt.class).reflectiveCalls);
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
                    // and the Blackbird failure came from the generated reader, except
                    // for the `List` property, which stays on the stock path
                    Throwable root = actual;
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    if (!json.contains("list")) {
                        assertTrue(Arrays.stream(root.getStackTrace())
                                .noneMatch(e -> e.getClassName().equals(MethodProperty.class.getName())), desc);
                    }
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

    @Test
    public void testReplacedBuilderOfBaseClass() throws Exception
    {
        // Since 3.2, databind rejects a builder replaced by an instance of another type
        String json = a2q("{'first':1,'x':2}");
        Throwable expected = assertThrows(Throwable.class,
                () -> newVanillaJSONMapper().readValue(json, ReplacingBuilt.class));
        Throwable actual = assertThrows(Throwable.class,
                () -> MAPPER.readValue(json, ReplacingBuilt.class));
        _assertSameFailure(json, expected, actual);
    }

    // A builder that isn't an instance of the setter's class (or is null) must fail
    // as reflection does, not as a setter failure from the generated cast
    @Test
    public void testUnusableBuilderInstanceMatchesVanilla() throws Exception
    {
        assertGeneratedReader(MAPPER, UnrelatedBuilt.class);
        assertGeneratedReader(MAPPER, NullCreatorBuilt.class);
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
                    assertFalse(expected instanceof tools.jackson.databind.exc.UnrecognizedPropertyException,
                            desc);
                    _assertSameFailure(desc, expected, actual);
                }
            }
        }
    }

    @Test
    public void testInheritedPackagePrivateSetter() throws Exception
    {
        assertEquals(3, newVanillaJSONMapper().readValue(a2q("{'y':3}"), HiddenSetterBuilt.class).y);
        assertEquals(3, MAPPER.readValue(a2q("{'y':3}"), HiddenSetterBuilt.class).y);
    }

    // Not even if databind made it accessible first, as it does for injection
    @Test
    public void testInjectedPackagePrivateSetterInOtherPackage() throws Exception
    {
        InjectableValues inject = new InjectableValues.Std().addValue("tag", "injected");
        for (String json : new String[] { a2q("{'tag':'json'}"), "{}" }) {
            InjectedSetterBuilt expected = JsonMapper.builder().injectableValues(inject).build()
                    .readValue(json, InjectedSetterBuilt.class);
            InjectedSetterBuilt actual = mapperBuilder().injectableValues(inject).build()
                    .readValue(json, InjectedSetterBuilt.class);
            assertEquals(expected.tag, actual.tag, json);
        }
    }

    // `setAndReturn` with a builder the setter can't take fails as reflection does,
    // and skips nulls
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
                        () -> _findProperty(vanilla, name).setAndReturn(CTXT, instance, c[1]), desc);
                Throwable actual = assertThrows(Throwable.class,
                        () -> _findProperty(MAPPER, name).setAndReturn(CTXT, instance, c[1]), desc);
                _assertSameFailure(desc + ", " + expected, expected, actual);
            }
        }
        for (String name : new String[] { "str", "list" }) {
            SettableBeanProperty prop = _findProperty(MAPPER, SkipNullsBuilt.class, name);
            SkipNullsBuilder builder = new SkipNullsBuilder();
            assertSame(builder, prop.setAndReturn(CTXT, builder, null));
            assertEquals("default", builder.str);
            assertEquals(Arrays.asList("keep"), builder.list);
        }
    }

    @Test
    public void testNullReturningBuilderForAllTypes() throws Exception
    {
        assertGeneratedReader(MAPPER, NullReturningAllBuilt.class);
        NullReturningAllBuilt v = MAPPER.readValue(a2q("{'l':7,'b':true,'list':['x']}"), NullReturningAllBuilt.class);
        assertEquals(7L, v.l);
        assertTrue(v.b);
        assertEquals(Arrays.asList("x"), v.list);
        // and through setAndReturn
        NullReturningAllBuilder builder = new NullReturningAllBuilder();
        assertSame(builder, _findProperty(MAPPER, NullReturningAllBuilt.class, "l").setAndReturn(CTXT, builder, 3L));
        assertSame(builder, _findProperty(MAPPER, NullReturningAllBuilt.class, "b").setAndReturn(CTXT, builder, true));
        assertSame(builder, _findProperty(MAPPER, NullReturningAllBuilt.class, "list")
                .setAndReturn(CTXT, builder, Collections.singletonList("y")));
    }

    @Test
    public void testBuilderNullHandlingMatchesVanilla() throws Exception
    {
        ObjectMapper vanilla = JsonMapper.builder()
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT).build();
        ObjectMapper blackbird = mapperBuilder()
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT).build();
        assertGeneratedReader(blackbird, NullsBuilt.class);
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
            MutableBuilder expectedBuilder = new MutableBuilder();
            MutableBuilder actualBuilder = new MutableBuilder();
            Throwable expected = assertThrows(Throwable.class,
                    () -> expectedProperty.setAndReturn(CTXT, expectedBuilder, null), name);
            Throwable actual = assertThrows(Throwable.class,
                    () -> actualProperty.setAndReturn(CTXT, actualBuilder, null), name);
            _assertSameFailure(name + ", " + expected, expected, actual);
            assertEquals(0, actualBuilder.reflectiveCalls, name);

            SettableBeanProperty skipping = actualProperty.withNullProvider(NullsConstantProvider.skipper());
            assertSame(actualBuilder, skipping.setAndReturn(CTXT, actualBuilder, null), name);
            assertEquals(0, actualBuilder.reflectiveCalls, name);
        }
    }

    // The generated reader may only call what `MethodProperty`'s method handle could:
    // the protected setters only if Jackson made them accessible, and (with databind in
    // its own module, which doesn't read this one) the public one too
    @Test
    public void testAccessModifierOverridesMatchVanilla() throws Exception
    {
        String json = a2q("{'value':42}");
        String bufferedJson = a2q("{'value':42,'id':1}");
        for (boolean canOverride : new boolean[] { true, false }) {
            for (boolean overridePublic : new boolean[] { true, false }) {
                String desc = "canOverride=" + canOverride + ", overridePublic=" + overridePublic;
                ObjectMapper vanilla = JsonMapper.builder()
                        .configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, canOverride)
                        .configure(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS, overridePublic).build();
                ObjectMapper blackbird = mapperBuilder()
                        .configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, canOverride)
                        .configure(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS, overridePublic).build();

                if (!canOverride) {
                    // the protected setter isn't called (and fails as with reflection),
                    // including for a property buffered before the creator property,
                    // and a builder without a build method (which is still a builder)
                    for (String j : new String[] { json, bufferedJson }) {
                        for (Class<?> type : new Class<?>[] { ProtectedSetterBuilt.class,
                                ProtectedCreatorSetterBuilt.class, NoBuildMethodBuilder.class }) {
                            protectedCalls = 0;
                            assertThrows(Throwable.class, () -> blackbird.readValue(j, type), desc);
                            assertEquals(0, protectedCalls, desc);
                        }
                    }
                } else if (overridePublic) {
                    // Jackson made everything accessible, so these work, as they do without Blackbird
                    assertEquals(42, vanilla.readValue(json, ProtectedSetterBuilt.class).value, desc);
                    assertEquals(42, blackbird.readValue(json, ProtectedSetterBuilt.class).value, desc);
                    assertEquals(42, blackbird.readValue(bufferedJson, ProtectedCreatorSetterBuilt.class).value, desc);
                    assertEquals(42, blackbird.readValue(bufferedJson, NoBuildMethodBuilder.class).value, desc);
                    PublicSetterBuilt result = blackbird.readValue(json, PublicSetterBuilt.class);
                    assertEquals(42, result.value, desc);
                    assertEquals(0, result.reflectiveCalls, desc);
                }
            }
        }
    }

    @Test
    public void testValueOfAnotherTypeMatchesVanilla() throws Exception
    {
        assertGeneratedReader(MAPPER, ChildBuilt.class);
        String json = a2q("{'child':{}}");
        Throwable expected = assertThrows(Throwable.class, () -> newVanillaJSONMapper().readValue(json, ChildBuilt.class));
        Throwable actual = assertThrows(Throwable.class, () -> MAPPER.readValue(json, ChildBuilt.class));
        _assertSameFailure(String.valueOf(expected), expected, actual);

        String bufferedJson = a2q("{'child':{},'id':1}");
        expected = assertThrows(Throwable.class,
                () -> newVanillaJSONMapper().readValue(bufferedJson, CreatorChildBuilt.class));
        actual = assertThrows(Throwable.class, () -> MAPPER.readValue(bufferedJson, CreatorChildBuilt.class));
        _assertSameFailure(String.valueOf(expected), expected, actual);
    }

    // `set()` with a value the setter doesn't declare (which reflection may widen or
    // reject), or an instance it doesn't take, behaves as with reflection
    @Test
    public void testSetWithValueOfAnotherTypeMatchesVanilla() throws Exception
    {
        ObjectMapper vanilla = newVanillaJSONMapper();
        // name, a valid value, then others
        Object[][] cases = {
            { "str", "foo", 42, new Object() },
            { "list", Arrays.asList("a"), "x", 42 },
            { "i", 1, 42L, "x", true, (short) 3, 'c' },
            { "l", 1L, "x", 2.5, 3, (byte) 4 },
            { "b", true, "x", 1 },
        };
        for (Object[] c : cases) {
            String name = (String) c[0];
            SettableBeanProperty expectedProp = _findProperty(vanilla, VoidSetterBuilt.class, name);
            SettableBeanProperty actualProp = _findProperty(MAPPER, VoidSetterBuilt.class, name);
            for (int i = 2; i < c.length; i++) {
                Object value = c[i];
                String desc = name + " = " + value + " (" + value.getClass().getSimpleName() + ")";
                VoidSetterBuilder expectedBuilder = new VoidSetterBuilder();
                VoidSetterBuilder actualBuilder = new VoidSetterBuilder();
                Throwable expected = null;
                try {
                    expectedProp.set(CTXT, expectedBuilder, value);
                } catch (Throwable t) {
                    expected = t;
                }
                if (expected == null) {
                    actualProp.set(CTXT, actualBuilder, value);
                    assertEquals(expectedBuilder.i, actualBuilder.i, desc);
                    assertEquals(expectedBuilder.l, actualBuilder.l, desc);
                } else {
                    Throwable actual = assertThrows(Throwable.class, () -> actualProp.set(CTXT, actualBuilder, value), desc);
                    _assertSameFailure(desc + ", " + expected, expected, actual);
                }
            }
            Object value = c[1];
            Throwable expected = assertThrows(Throwable.class, () -> expectedProp.set(CTXT, "not a builder", value), name);
            Throwable actual = assertThrows(Throwable.class, () -> actualProp.set(CTXT, "not a builder", value), name);
            _assertSameFailure(name + ", " + expected, expected, actual);
        }
    }

    @Test
    public void testPolymorphicNullMatchesVanilla() throws Exception
    {
        assertGeneratedReader(MAPPER, ItemBuilt.class);
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
    // others, and Blackbird must not cast or truncate them instead.
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
            assertEquals(_setAndReturn(vanilla, name, c[1]), _setAndReturn(MAPPER, name, c[1]),
                    name + "=" + c[1] + " (" + c[1].getClass().getSimpleName() + ")");
        }
    }

    private static String _setAndReturn(ObjectMapper mapper, String name, Object value) throws Exception {
        try {
            MutableBuilder b = (MutableBuilder) _findProperty(mapper, name).setAndReturn(CTXT, new MutableBuilder(), value);
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
        BeanDeserializerBase deser = (BeanDeserializerBase) mapper._deserializationContext()
                .findRootValueDeserializer(mapper.constructType(type));
        return deser.findProperty(PropertyName.construct(name));
    }

    private static void _assertSameFailure(String desc, Throwable expected, Throwable actual) {
        assertEquals(expected.getClass(), actual.getClass(), desc);
        if (expected instanceof DatabindException) {
            DatabindException e = (DatabindException) expected;
            DatabindException a = (DatabindException) actual;
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
