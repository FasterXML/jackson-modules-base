package com.fasterxml.jackson.module.blackbird.ser;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// `NullSerializer` (a standard serializer, often used to hide a value) must
// still write null for every optimized property type
public class TestNullSerializer extends BlackbirdTestBase
{
    @JsonPropertyOrder({ "i", "l", "d", "b", "s", "list" })
    public static class Annotated {
        @JsonSerialize(using = NullSerializer.class)
        public int getI() { return 1; }
        @JsonSerialize(using = NullSerializer.class)
        public long getL() { return 2L; }
        @JsonSerialize(using = NullSerializer.class)
        public double getD() { return 3.5; }
        @JsonSerialize(using = NullSerializer.class)
        public boolean isB() { return true; }
        @JsonSerialize(using = NullSerializer.class)
        public String getS() { return "s"; }
        @JsonSerialize(using = NullSerializer.class)
        public List<String> getList() { return Arrays.asList("x"); }
    }

    @JsonPropertyOrder({ "i", "l", "d", "b", "s" })
    public static class Plain {
        public int getI() { return 1; }
        public long getL() { return 2L; }
        public double getD() { return 3.5; }
        public boolean isB() { return true; }
        public String getS() { return "s"; }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "i", "l", "d", "b", "s" })
    public static class AsArray extends Plain { }

    private static final String NULLS = a2q("{'i':null,'l':null,'d':null,'b':null,'s':null}");

    @Test
    public void testAnnotated() throws Exception
    {
        String expected = newVanillaJSONMapper().writeValueAsString(new Annotated());
        assertEquals(a2q("{'i':null,'l':null,'d':null,'b':null,'s':null,'list':null}"), expected);
        assertEquals(expected, newObjectMapper().writeValueAsString(new Annotated()));
    }

    // and registered for each type, so assigned when the serializer is resolved
    @Test
    public void testRegistered() throws Exception
    {
        SimpleModule nulls = new SimpleModule();
        for (Class<?> type : new Class<?>[] { int.class, long.class, double.class, boolean.class, String.class }) {
            nulls.addSerializer(type, NullSerializer.instance);
        }
        ObjectMapper vanilla = JsonMapper.builder().addModule(nulls).build();
        ObjectMapper blackbird = mapperBuilder().addModule(nulls).build();
        assertEquals(NULLS, vanilla.writeValueAsString(new Plain()));
        assertEquals(NULLS, blackbird.writeValueAsString(new Plain()));
        assertEquals("[null,null,null,null,null]", vanilla.writeValueAsString(new AsArray()));
        assertEquals("[null,null,null,null,null]", blackbird.writeValueAsString(new AsArray()));
    }
}
