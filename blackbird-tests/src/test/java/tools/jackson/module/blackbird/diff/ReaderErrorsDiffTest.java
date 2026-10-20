package tools.jackson.module.blackbird.diff;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.module.blackbird.deser.BBDeserializerModifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Failures inside generated POJO and builder codecs: constructors, build
 * methods and setters fail with the exception class, message and reference
 * path of stock, with and without WRAP_EXCEPTIONS. A builder setter that
 * returns null keeps the current builder, as in stock. Every scenario's bean
 * keeps the generated codec.
 */
public class ReaderErrorsDiffTest extends DifferentialTestBase
{
    /*
    /**********************************************************************
    /* Constructor and build() failures
    /**********************************************************************
     */

    public static class ThrowingCtor {
        static final ThreadLocal<Boolean> FAIL = ThreadLocal.withInitial(() -> true);

        public String a;

        public ThrowingCtor() {
            if (FAIL.get()) {
                throw new IllegalStateException("no default instance");
            }
        }

        static ThrowingCtor created() {
            FAIL.set(false);
            try {
                return new ThrowingCtor();
            } finally {
                FAIL.remove();
            }
        }
    }

    @Test
    public void constructorFailureIsValueInstantiationException() {
        Mappers m = mappers();
        ValueInstantiationException e = assertSameRead(m, "{\"a\":\"x\"}", ThrowingCtor.class)
                .error(ValueInstantiationException.class);
        assertTrue(e.getMessage().contains("no default instance"), e.getMessage());
        assertSameRead(mappers(b -> b.disable(DeserializationFeature.WRAP_EXCEPTIONS)),
                "{\"a\":\"x\"}", ThrowingCtor.class);
        assertGeneratedReader(m.blackbird(), ThrowingCtor.class);
    }

    static final class CtorHandler extends DeserializationProblemHandler {
        @Override
        public Object handleInstantiationProblem(DeserializationContext ctxt, Class<?> instClass,
                Object argument, Throwable t) {
            return (instClass == ThrowingCtor.class) ? ThrowingCtor.created() : NOT_HANDLED;
        }
    }

    @Test
    public void problemHandlerReplacesFailedConstruction() {
        Mappers m = mappers(b -> b.addHandler(new CtorHandler()));
        ThrowingCtor t = assertSameRead(m, "{\"a\":\"x\"}", ThrowingCtor.class).get();
        assertEquals("x", t.a);
        assertGeneratedReader(m.blackbird(), ThrowingCtor.class);
    }

    @JsonDeserialize(builder = Immutable.Builder.class)
    public static class Immutable {
        public final String name;
        public final int count;

        Immutable(String name, int count) {
            this.name = name;
            this.count = count;
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            private String name;
            private int count;

            public Builder name(String n) { name = n; return this; }
            public Builder count(int c) { count = c; return this; }

            public Immutable build() {
                if (name == null) {
                    throw new IllegalStateException(
                            "Cannot build Immutable, some of required attributes are not set [name]");
                }
                return new Immutable(name, count);
            }
        }
    }

    @Test
    public void buildFailureIsValueInstantiationException() {
        Mappers m = mappers();
        assertEquals("a", assertSameRead(m, "{\"name\":\"a\",\"count\":1}", Immutable.class)
                .<Immutable>get().name);
        ValueInstantiationException e = assertSameRead(m, "{\"count\":1}", Immutable.class)
                .error(ValueInstantiationException.class);
        assertTrue(e.getMessage().contains("required attributes"), e.getMessage());
        assertSameRead(mappers(b -> b.disable(DeserializationFeature.WRAP_EXCEPTIONS)),
                "{\"count\":1}", Immutable.class);
        assertGeneratedReader(m.blackbird(), Immutable.class);
    }

    /*
    /**********************************************************************
    /* Setter failures
    /**********************************************************************
     */

    public static class Child {
        public String name;
    }

    public static class Guarded {
        private int age;
        private String code;
        private Child child;
        public String other;

        public int getAge() { return age; }

        public void setAge(int age) {
            if (age < 0) {
                throw new IllegalArgumentException("age must be >= 0");
            }
            this.age = age;
        }

        public String getCode() { return code; }

        public void setCode(String code) {
            if (code.isEmpty()) {
                throw new IllegalStateException("empty code");
            }
            this.code = code;
        }

        public Child getChild() { return child; }

        public void setChild(Child child) {
            if (child.name == null) {
                throw new IllegalArgumentException(null, null);
            }
            this.child = child;
        }
    }

    @Test
    public void setterIllegalArgument() {
        Mappers m = mappers();
        DatabindException e = assertSameRead(m, "{\"other\":\"o\",\"age\":-1}", Guarded.class)
                .error(DatabindException.class);
        assertTrue(e.getMessage().startsWith("Problem deserializing property 'age'"),
                e.getMessage());
        // Stock turns the IllegalArgumentException into a DatabindException
        // even without wrapping.
        assertSameRead(mappers(b -> b.disable(DeserializationFeature.WRAP_EXCEPTIONS)),
                "{\"age\":-1}", Guarded.class).error(DatabindException.class);
        // A child value: the message names its class; no message from the setter.
        assertSameRead(m, "{\"child\":{}}", Guarded.class).error(DatabindException.class);
        String layout = assertGeneratedReader(m.blackbird(), Guarded.class);
        assertArm(layout, "age=INT");
        assertArm(layout, "child=CHILD");
    }

    @Test
    public void setterOtherRuntimeException() {
        Mappers m = mappers();
        assertSameRead(m, "{\"code\":\"\"}", Guarded.class).error(DatabindException.class);
        assertSameRead(mappers(b -> b.disable(DeserializationFeature.WRAP_EXCEPTIONS)),
                "{\"code\":\"\"}", Guarded.class).error(IllegalStateException.class);
        assertArm(assertGeneratedReader(m.blackbird(), Guarded.class), "code=STRING");
    }

    @JsonDeserialize(builder = GuardedBuilt.Builder.class)
    public static class GuardedBuilt {
        public final int age;

        GuardedBuilt(int age) {
            this.age = age;
        }

        @JsonPOJOBuilder(withPrefix = "with")
        public static class Builder {
            private int age;

            public Builder withAge(int a) {
                if (a < 0) {
                    throw new IllegalArgumentException("age must be >= 0");
                }
                age = a;
                return this;
            }

            public GuardedBuilt build() { return new GuardedBuilt(age); }
        }
    }

    @Test
    public void builderSetterIllegalArgument() {
        Mappers m = mappers();
        assertSameRead(m, "{\"age\":-1}", GuardedBuilt.class).error(DatabindException.class);
        assertArm(assertGeneratedReader(m.blackbird(), GuardedBuilt.class), "age=INT");
    }

    /*
    /**********************************************************************
    /* Builder setters that return null
    /**********************************************************************
     */

    // A setter declared to return the builder may return null; stock
    // deserializeSetAndReturn then keeps the current builder.
    @JsonDeserialize(builder = NullFluent.Builder.class)
    public static class NullFluent {
        public final String a;
        public final int n;
        public final Child child;
        public final String b;

        NullFluent(String a, int n, Child child, String b) {
            this.a = a;
            this.n = n;
            this.child = child;
            this.b = b;
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            private String a;
            private int n;
            private Child child;
            private String b;

            public Builder a(String v) { a = v; return null; }
            public Builder n(int v) { n = v; return null; }
            public Builder child(Child v) { child = v; return null; }
            public Builder b(String v) { b = v; return this; }

            public NullFluent build() { return new NullFluent(a, n, child, b); }
        }
    }

    @Test
    public void builderSetterReturningNullKeepsBuilder() {
        Mappers m = mappers();
        NullFluent v = assertSameRead(m,
                "{\"a\":\"x\",\"n\":3,\"child\":{\"name\":\"c\"},\"b\":\"y\"}", NullFluent.class)
                .get();
        assertEquals("x", v.a);
        assertEquals(3, v.n);
        assertEquals("c", v.child.name);
        assertEquals("y", v.b);
        String layout = assertGeneratedReader(m.blackbird(), NullFluent.class);
        assertArm(layout, "a=STRING");
        assertArm(layout, "n=INT");
        assertArm(layout, "child=CHILD");
        assertArm(layout, "b=STRING");
    }

    /*
    /**********************************************************************
    /* No thread-local hand-off of the build method
    /**********************************************************************
     */

    @Test
    public void modifierKeepsNoThreadLocalState() {
        for (Field f : BBDeserializerModifier.class.getDeclaredFields()) {
            assertFalse(ThreadLocal.class.isAssignableFrom(f.getType()), f.toString());
        }
        Mappers m = mappers();
        assertSameRead(m, "{\"name\":\"a\",\"count\":1}", Immutable.class);
        assertGeneratedReader(m.blackbird(), Immutable.class);
    }
}
