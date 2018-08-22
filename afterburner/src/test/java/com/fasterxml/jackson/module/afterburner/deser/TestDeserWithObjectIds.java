package com.fasterxml.jackson.module.afterburner.deser;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestDeserWithObjectIds extends AfterburnerTestBase {

    @Test
    public void testDeserializationWithObjectIds() throws IOException {

        Outer outer = new Outer();
        outer.inners.add(new Inner(outer));
        outer.inners.add(new Inner(outer));

        String json = objectMapper().writeValueAsString(outer);

        Outer result = objectMapper().readValue(json, Outer.class);

        assertTrue(result.inners.size() > 0);
        assertEquals(result, result.inners.get(0).outer);
    }

    @Test
    public void testDeserializeWithObjectIdsSuperSonicBeanDeser() throws IOException {

        OuterWithManyProperties outer = new OuterWithManyProperties();
        outer.inners.add(new Inner(outer));
        outer.inners.add(new Inner(outer));

        String json = objectMapper().writeValueAsString(outer);

        OuterWithManyProperties result = objectMapper().readValue(json, OuterWithManyProperties.class);

        assertTrue(result.inners.size() > 0);
        assertEquals(result, result.inners.get(0).outer);
    }

    @SuppressWarnings("WeakerAccess")
    @JsonIdentityInfo(
            generator = ObjectIdGenerators.UUIDGenerator.class
    )
    public static class Outer {

        public List<Inner> inners = new ArrayList<>();
    }

    @JsonIdentityInfo(
            generator = ObjectIdGenerators.UUIDGenerator.class
    )
    public static class OuterWithManyProperties extends Outer {

        public String prop1 = "prop1";
        public String prop2 = "prop2";
        public String prop3 = "prop3";
        public String prop4 = "prop4";
        public String prop5 = "prop5";
        public String prop6 = "prop6";
        public String prop7 = "prop7";
        public String prop8 = "prop8";
        public String prop9 = "prop9";
        public String prop10 = "prop10";
    }

    @SuppressWarnings("WeakerAccess")
    @JsonIdentityInfo(
            generator = ObjectIdGenerators.UUIDGenerator.class
    )
    public static class Inner {

        public Outer outer;

        public Inner(Outer outer) {
            this.outer = outer;
        }

        public Inner() {

        }
    }
}
