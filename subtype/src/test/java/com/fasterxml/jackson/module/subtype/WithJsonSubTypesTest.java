package com.fasterxml.jackson.module.subtype;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.*;

/**
 * test {@link JsonSubType} work with {@link JsonSubTypes}
 */
@RunWith(value = Parameterized.class)
public class WithJsonSubTypesTest<T extends WithJsonSubTypesTest.Parent> {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new SubtypeModule());

    public static class Argument<T> {
        private final Class<T> clazz;
        private final T expected;

        public Argument(Class<T> clazz, T expected) {
            this.clazz = clazz;
            this.expected = expected;
        }
    }

    @Parameter
    public Argument<T> argument;

    @Parameters
    public static Collection<Argument<?>> data() {
        return Arrays.asList(
                new Argument<>(FirstChild.class, new FirstChild("hello")),
                new Argument<>(SecondChild.class, new SecondChild("world")),
                new Argument<>(FirstAppendChild.class, new FirstAppendChild(42)),
                new Argument<>(SecondAppendChild.class, new SecondAppendChild("42", Arrays.asList("hello", "foo", "bar"))),
                new Argument<>(ThirdAppendChild.class, new ThirdAppendChild("42", Arrays.asList("hello", "foo", "bar"), 3.1415926))
        );
    }

    @Test
    public void test() throws Exception {
        final Parent parent = argument.expected;
        String json = mapper.writeValueAsString(parent);
        Parent unmarshal = mapper.readValue(json, Parent.class);
        T actual = assertInstanceOf(argument.clazz, unmarshal);
        assertEquals(argument.expected, actual);
    }

    public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue) {
        assertTrue(expectedType.isInstance(actualValue));
        return expectedType.cast(actualValue);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(value = {
            @JsonSubTypes.Type(value = FirstChild.class, name = "first-child"),
            @JsonSubTypes.Type(value = SecondChild.class, name = "second-child"),
    })
    public interface Parent {
    }

    public static class FirstChild implements Parent {
        public String foo;

        @SuppressWarnings("unused") // SPI require it
        public FirstChild() {
        }

        public FirstChild(String foo) {
            this.foo = foo;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FirstChild that = (FirstChild) o;

            return Objects.equals(foo, that.foo);
        }

        @Override
        public int hashCode() {
            return foo != null ? foo.hashCode() : 0;
        }
    }

    public static class SecondChild implements Parent {
        public String bar;

        public SecondChild() {
        }

        public SecondChild(String bar) {
            this.bar = bar;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SecondChild that = (SecondChild) o;

            return Objects.equals(bar, that.bar);
        }

        @Override
        public int hashCode() {
            return bar != null ? bar.hashCode() : 0;
        }
    }

    @JsonSubType("first-append-child")
    @AutoService(Parent.class)
    public static class FirstAppendChild implements Parent {
        public Integer integer;

        @SuppressWarnings("unused") // SPI require it
        public FirstAppendChild() {
        }

        public FirstAppendChild(Integer integer) {
            this.integer = integer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FirstAppendChild that = (FirstAppendChild) o;

            return Objects.equals(integer, that.integer);
        }

        @Override
        public int hashCode() {
            return integer != null ? integer.hashCode() : 0;
        }
    }


    @JsonSubType("second-append-child")
    @AutoService(Parent.class)
    public static class SecondAppendChild extends SecondChild {
        public List<String> list;

        public SecondAppendChild() {
        }

        public SecondAppendChild(String bar, List<String> list) {
            super(bar);
            this.list = list;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            SecondAppendChild that = (SecondAppendChild) o;

            return Objects.equals(list, that.list);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (list != null ? list.hashCode() : 0);
            return result;
        }
    }

    @JsonSubType("third-append-child")
    @AutoService(Parent.class)
    public static class ThirdAppendChild extends SecondAppendChild {
        public double value;

        @SuppressWarnings("unused") // SPI require it
        public ThirdAppendChild() {
        }

        public ThirdAppendChild(String bar, List<String> list, double value) {
            super(bar, list);
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            ThirdAppendChild that = (ThirdAppendChild) o;

            return Double.compare(value, that.value) == 0;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            long temp;
            temp = Double.doubleToLongBits(value);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }
}