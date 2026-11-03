package com.fasterxml.jackson.module.blackbird.ser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// Unwrapping with a prefix renames the optimized writers after their serializers
// were assigned; a renamed writer must still write what `BeanPropertyWriter` writes
public class TestUnwrappedWriters extends BlackbirdTestBase
{
    @JsonPropertyOrder({ "i", "l", "d", "b", "s", "list" })
    public static class Plain {
        public int getI() { return 1; }
        public long getL() { return 2L; }
        public double getD() { return 3.5; }
        public boolean isB() { return true; }
        public String getS() { return "s"; }
        public List<String> getList() { return Arrays.asList("x"); }
    }

    @JsonPropertyOrder({ "i", "l", "d", "b", "s", "list" })
    public static class Shaped {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public int getI() { return 1; }
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public long getL() { return 2L; }
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public double getD() { return 3.5; }
        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        public boolean isB() { return true; }
    }

    public static class Outer {
        @JsonUnwrapped(prefix = "p_")
        public Object inner;

        Outer(Object inner) { this.inner = inner; }
    }

    // Writes the negated value, for each primitive type
    static class Negating<T extends Number> extends StdSerializer<T> {
        private static final long serialVersionUID = 1L;

        Negating(Class<T> type) { super(type); }

        @Override
        public void serialize(T value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeNumber(-value.doubleValue());
        }
    }

    // Fails when called through the generated accessor, as in `TestAccessFallback`
    static <T> T failFromGeneratedAccessor(T value) {
        for (StackTraceElement elem : new Throwable().getStackTrace()) {
            if (elem.getClassName().equals("java.lang.reflect.Method")) {
                return value;
            }
            if (elem.getClassName().startsWith("com.fasterxml.jackson.module.blackbird.ser.")
                    && elem.getClassName().endsWith("PropertyWriter")) {
                throw new IllegalAccessError("simulated");
            }
        }
        return value;
    }

    @JsonPropertyOrder({ "i", "l", "d", "b", "s", "list" })
    public static class Failing {
        public int getI() { return failFromGeneratedAccessor(1); }
        public long getL() { return failFromGeneratedAccessor(2L); }
        public double getD() { return failFromGeneratedAccessor(3.5); }
        public boolean isB() { return failFromGeneratedAccessor(true); }
        public String getS() { return failFromGeneratedAccessor("s"); }
        public List<String> getList() { return failFromGeneratedAccessor(Arrays.asList("x")); }
    }

    private static final List<UnaryOperator<JsonMapper.Builder>> CONFIGS = Arrays.asList(
        b -> b,
        b -> b.addModule(new SimpleModule()
                .addSerializer(Integer.TYPE, new Negating<>(Integer.class))
                .addSerializer(Long.TYPE, new Negating<>(Long.class))
                .addSerializer(Double.TYPE, new Negating<>(Double.class))),
        b -> b.withConfigOverride(Long.TYPE, o -> o.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)))
    );

    @Test
    public void testMatchesVanilla() throws Exception
    {
        for (int i = 0; i < CONFIGS.size(); i++) {
            ObjectMapper vanilla = CONFIGS.get(i).apply(JsonMapper.builder()).build();
            ObjectMapper blackbird = CONFIGS.get(i).apply(mapperBuilder()).build();
            for (Object inner : new Object[] { new Plain(), new Shaped() }) {
                String desc = "config " + i + ", " + inner.getClass().getSimpleName();
                // the inner bean on its own, then unwrapped
                assertEquals(vanilla.writeValueAsString(inner), blackbird.writeValueAsString(inner), desc);
                assertEquals(vanilla.writeValueAsString(new Outer(inner)),
                        blackbird.writeValueAsString(new Outer(inner)), desc);
            }
        }
        assertEquals(a2q("{'p_i':'1','p_l':'2','p_d':'3.5','p_b':1}"),
                newObjectMapper().writeValueAsString(new Outer(new Shaped())));
    }

    // and an access error falls back to a writer with the new name
    @Test
    public void testAccessErrorFallsBackWithNewName() throws Exception
    {
        String expected = newVanillaJSONMapper().writeValueAsString(new Outer(new Failing()));
        assertEquals(a2q("{'p_i':1,'p_l':2,'p_d':3.5,'p_b':true,'p_s':'s','p_list':['x']}"), expected);
        assertEquals(expected, newObjectMapper().writeValueAsString(new Outer(new Failing())));
    }
}
