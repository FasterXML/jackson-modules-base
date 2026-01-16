package com.fasterxml.jackson.module.spisubtypes;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.auto.service.AutoService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * test {@link JacksonSubType} works alone, without {@link JsonSubTypes}
 */
public class WithoutJacksonSubTypesTest {
    private final ObjectMapper mapper = JsonMapper.builder().addModule(new SubtypesModule()).build();

    @Test
    public void testFirstChild() throws Exception {
        FirstChild child = new FirstChild();
        child.foo = "hello";
        String json = mapper.writeValueAsString(child);

        // {"type":"first-child","foo":"hello"}

        Parent unmarshal = mapper.readValue(json, Parent.class);
        FirstChild actual = assertInstanceOf(FirstChild.class, unmarshal);
        assertEquals("hello", actual.foo);
    }

    @Test
    public void testSecondChild() throws Exception {
        SecondChild child = new SecondChild();
        child.bar = "world";
        String json = mapper.writeValueAsString(child);

        // {"type":"second-child","bar":"world"}

        Parent unmarshal = mapper.readValue(json, Parent.class);
        SecondChild actual = assertInstanceOf(SecondChild.class, unmarshal);
        assertEquals("world", actual.bar);
    }

    public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue) {
        assertTrue(expectedType.isInstance(actualValue));
        return expectedType.cast(actualValue);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    public interface Parent {
    }

    @JacksonSubType("first-child")
    @AutoService(Parent.class) // module requires spi
    public static class FirstChild implements Parent {
        public String foo;

        public FirstChild() {
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


    @JacksonSubType("second-child")
    @AutoService(Parent.class) // module requires spi
    public static class SecondChild implements Parent {
        public String bar;

        public SecondChild() {
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


}
