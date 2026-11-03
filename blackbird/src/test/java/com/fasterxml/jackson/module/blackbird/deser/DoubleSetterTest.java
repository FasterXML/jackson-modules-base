package com.fasterxml.jackson.module.blackbird.deser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.impl.MethodProperty;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// Primitive double setters should go through generated accessors, not reflection
public class DoubleSetterTest extends BlackbirdTestBase
{
    interface HasDouble {
        double d();
        int reflectiveCalls();
    }

    static class DoubleBean implements HasDouble
    {
        double d = 7.0;
        int reflectiveCalls;

        public void setD(double v) {
            reflectiveCalls += reflectiveCall();
            d = v;
        }

        @Override public double d() { return d; }
        @Override public int reflectiveCalls() { return reflectiveCalls; }
    }

    static class FluentDoubleBean implements HasDouble
    {
        double d = 7.0;
        int reflectiveCalls;

        public FluentDoubleBean setD(double v) {
            reflectiveCalls += reflectiveCall();
            d = v;
            return this;
        }

        @Override public double d() { return d; }
        @Override public int reflectiveCalls() { return reflectiveCalls; }
    }

    // `d` before `id` is buffered, and assigned through `set()`
    static class CreatorDoubleBean extends DoubleBean
    {
        @JsonCreator
        CreatorDoubleBean(@JsonProperty("id") int id) { }
    }

    // Each setter returns a new builder, so the returned value must be used
    static final class DoubleBuilder
    {
        final double d;
        final int reflectiveCalls;

        public DoubleBuilder() {
            this(7.0, 0);
        }

        DoubleBuilder(double d, int reflectiveCalls) {
            this.d = d;
            this.reflectiveCalls = reflectiveCalls;
        }

        public DoubleBuilder withD(double v) {
            return new DoubleBuilder(v, reflectiveCalls + reflectiveCall());
        }

        public DoubleBuilt build() {
            return new DoubleBuilt(d, reflectiveCalls);
        }
    }

    @JsonDeserialize(builder=DoubleBuilder.class)
    static class DoubleBuilt implements HasDouble
    {
        final double d;
        final int reflectiveCalls;

        DoubleBuilt(double d, int reflectiveCalls) {
            this.d = d;
            this.reflectiveCalls = reflectiveCalls;
        }

        @Override public double d() { return d; }
        @Override public int reflectiveCalls() { return reflectiveCalls; }
    }

    static final class SameDoubleBuilder
    {
        double d = 7.0;
        int reflectiveCalls;

        public SameDoubleBuilder withD(double v) {
            reflectiveCalls += reflectiveCall();
            d = v;
            return this;
        }

        public SameDoubleBuilt build() {
            return new SameDoubleBuilt(d, reflectiveCalls);
        }
    }

    @JsonDeserialize(builder=SameDoubleBuilder.class)
    static class SameDoubleBuilt extends DoubleBuilt {
        SameDoubleBuilt(double d, int reflectiveCalls) { super(d, reflectiveCalls); }
    }

    // A null result keeps the current builder
    static final class NullDoubleBuilder
    {
        double d = 7.0;
        int reflectiveCalls;

        public NullDoubleBuilder withD(double v) {
            reflectiveCalls += reflectiveCall();
            d = v;
            return null;
        }

        public NullDoubleBuilt build() {
            return new NullDoubleBuilt(d, reflectiveCalls);
        }
    }

    @JsonDeserialize(builder=NullDoubleBuilder.class)
    static class NullDoubleBuilt extends DoubleBuilt {
        NullDoubleBuilt(double d, int reflectiveCalls) { super(d, reflectiveCalls); }
    }

    // Buffered before a creator property, so assigned through `set()`
    static final class CreatorDoubleBuilder
    {
        double d = 7.0;
        int reflectiveCalls;

        @JsonCreator
        CreatorDoubleBuilder(@JsonProperty("id") int id) { }

        public CreatorDoubleBuilder withD(double v) {
            reflectiveCalls += reflectiveCall();
            d = v;
            return this;
        }

        public CreatorDoubleBuilt build() {
            return new CreatorDoubleBuilt(d, reflectiveCalls);
        }
    }

    @JsonDeserialize(builder=CreatorDoubleBuilder.class)
    static class CreatorDoubleBuilt extends DoubleBuilt {
        CreatorDoubleBuilt(double d, int reflectiveCalls) { super(d, reflectiveCalls); }
    }

    private static final List<Class<? extends HasDouble>> TYPES = Arrays.asList(
            DoubleBean.class, FluentDoubleBean.class, CreatorDoubleBean.class,
            DoubleBuilt.class, SameDoubleBuilt.class, NullDoubleBuilt.class, CreatorDoubleBuilt.class);

    static int reflectiveCall() {
        for (StackTraceElement elem : new Throwable().getStackTrace()) {
            if (elem.getClassName().equals(MethodProperty.class.getName())
                    || elem.getMethodName().equals("_reflectiveSetAndReturn")) {
                return 1;
            }
        }
        return 0;
    }

    private final ObjectMapper MAPPER = newObjectMapper();

    private static String json(Class<?> type, String value) {
        // the creator property after `d`, so `d` is buffered
        boolean creator = (type == CreatorDoubleBean.class) || (type == CreatorDoubleBuilt.class);
        return "{\"d\":" + value + (creator ? ",\"id\":1}" : "}");
    }

    @Test
    public void testSkipsReflection() throws Exception
    {
        for (Class<? extends HasDouble> type : TYPES) {
            assertTrue(_findProperty(MAPPER, type, "d") instanceof SettableDoubleProperty, type.getSimpleName());
            for (String value : new String[] { "1.25", "-0.0", "1e308", "4.9e-324" }) {
                HasDouble bean = MAPPER.readValue(json(type, value), type);
                String desc = type.getSimpleName() + ", " + value;
                assertEquals(Double.parseDouble(value), bean.d(), desc);
                assertEquals(Double.doubleToRawLongBits(Double.parseDouble(value)),
                        Double.doubleToRawLongBits(bean.d()), desc);
                assertEquals(0, bean.reflectiveCalls(), desc);
            }
        }
    }

    // Sanity check that the reflection probe works at all
    @Test
    public void testVanillaUsesReflection() throws Exception
    {
        ObjectMapper vanilla = newVanillaJSONMapper();
        for (Class<? extends HasDouble> type : TYPES) {
            assertEquals(1, vanilla.readValue(json(type, "1.25"), type).reflectiveCalls(), type.getSimpleName());
        }
    }

    // Non-float tokens go through the stock deserializer, including coercion rules
    @Test
    public void testNonFloatTokens() throws Exception
    {
        for (Class<? extends HasDouble> type : TYPES) {
            String desc = type.getSimpleName();
            assertEquals(1.0, MAPPER.readValue(json(type, "1"), type).d(), desc);
            assertEquals(1.5, MAPPER.readValue(json(type, "\"1.5\""), type).d(), desc);
            assertTrue(Double.isNaN(MAPPER.readValue(json(type, "\"NaN\""), type).d()), desc);
            assertEquals(0.0, MAPPER.readValue(json(type, "null"), type).d(), desc);
        }
    }

    @Test
    public void testIntCoercionFailure() throws Exception
    {
        ObjectMapper mapper = mapperBuilder()
                .withCoercionConfig(LogicalType.Float,
                        cfg -> cfg.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail))
                .build();
        for (Class<? extends HasDouble> type : TYPES) {
            assertThrows(MismatchedInputException.class, () -> mapper.readValue(json(type, "1"), type),
                    type.getSimpleName());
            // while floats aren't coerced at all
            assertEquals(1.5, mapper.readValue(json(type, "1.5"), type).d(), type.getSimpleName());
        }
    }

    @Test
    public void testNullForPrimitiveFailure() throws Exception
    {
        ObjectMapper mapper = mapperBuilder()
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        for (Class<? extends HasDouble> type : TYPES) {
            assertThrows(MismatchedInputException.class, () -> mapper.readValue(json(type, "null"), type),
                    type.getSimpleName());
        }
    }

    // Every input, and every setting that could affect it, as plain Jackson reads it
    @Test
    public void testInputsMatchVanilla() throws Exception
    {
        String[] values = {
            "1.25", "-0.0", "0.0", "1e308", "1e400", "-1e400", "4.9e-324", "2e-324",
            "0.1000000000000000055511151231257827", "123456789012345678901234567890.5",
            "1", "-1", "0", "9007199254740993", "123456789012345678901234567890",
            "\"1.5\"", "\"NaN\"", "\"Infinity\"", "\"-Infinity\"", "\"\"", "\" \"", "\"x\"",
            "null", "true", "[1.5]", "[1]", "[]", "[null]", "[[1.5]]", "{}", "NaN", "Infinity"
        };
        List<UnaryOperator<JsonMapper.Builder>> configs = Arrays.asList(
            b -> b,
            b -> b.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES),
            b -> b.enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS),
            b -> b.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT),
            b -> b.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS),
            b -> b.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS),
            b -> b.enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER),
            b -> b.withCoercionConfig(LogicalType.Float,
                    cfg -> cfg.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)),
            b -> b.withCoercionConfig(LogicalType.Float,
                    cfg -> cfg.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsEmpty)),
            b -> b.withCoercionConfigDefaults(cfg -> cfg.setCoercion(CoercionInputShape.Integer, CoercionAction.AsNull))
        );
        for (int i = 0; i < configs.size(); i++) {
            ObjectMapper vanilla = configs.get(i).apply(JsonMapper.builder()).build();
            ObjectMapper blackbird = configs.get(i).apply(mapperBuilder()).build();
            for (Class<? extends HasDouble> type : TYPES) {
                assertTrue(_findProperty(blackbird, type, "d") instanceof SettableDoubleProperty);
                for (String value : values) {
                    String json = json(type, value);
                    String desc = "config " + i + ", " + type.getSimpleName() + ", " + json;
                    assertEquals(_read(vanilla, json, type), _read(blackbird, json, type), desc);
                }
            }
        }
    }

    // Through a buffer or a tree, a float token may hold another number type
    @Test
    public void testBufferedNumbersMatchVanilla() throws Exception
    {
        ObjectMapper vanilla = newVanillaJSONMapper();
        JsonNodeFactory nodes = JsonNodeFactory.instance;
        List<Object> numbers = Arrays.asList(0.1f, Float.NaN, -0.0f, 1.5, Double.NaN, -0.0,
                new BigDecimal("0.1"), new BigDecimal("1e400"), BigInteger.TEN.pow(400), 7, 9007199254740993L);
        List<JsonNode> trees = Arrays.asList(nodes.numberNode(0.1f), nodes.numberNode(Double.NaN),
                nodes.numberNode(new BigDecimal("0.1")), nodes.numberNode(new BigDecimal("1e400")),
                nodes.numberNode(9007199254740993L), nodes.textNode("2.5"));
        for (Class<? extends HasDouble> type : TYPES) {
            boolean creator = (type == CreatorDoubleBean.class) || (type == CreatorDoubleBuilt.class);
            for (Object number : numbers) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("d", number);
                if (creator) {
                    map.put("id", 1);
                }
                String desc = type.getSimpleName() + ", " + number + " (" + number.getClass().getSimpleName() + ")";
                assertEquals(_convert(vanilla, map, type), _convert(MAPPER, map, type), desc);
            }
            for (JsonNode node : trees) {
                JsonNode tree = nodes.objectNode().set("d", node);
                if (creator) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) tree).put("id", 1);
                }
                String desc = type.getSimpleName() + ", " + tree;
                assertEquals(_treeToValue(vanilla, tree, type), _treeToValue(MAPPER, tree, type), desc);
            }
        }
        // and a float still goes through the generated setter
        DoubleBean bean = MAPPER.convertValue(Collections.singletonMap("d", 0.1f), DoubleBean.class);
        assertEquals((double) 0.1f, bean.d);
        assertEquals(0, bean.reflectiveCalls);
    }

    private static String _read(ObjectMapper mapper, String json, Class<? extends HasDouble> type) {
        try {
            return _describe(mapper.readValue(json, type));
        } catch (Exception e) {
            return _describe(e);
        }
    }

    private static String _convert(ObjectMapper mapper, Object value, Class<? extends HasDouble> type) {
        try {
            return _describe(mapper.convertValue(value, type));
        } catch (Exception e) {
            return _describe(e);
        }
    }

    private static String _treeToValue(ObjectMapper mapper, JsonNode tree, Class<? extends HasDouble> type) {
        try {
            return _describe(mapper.treeToValue(tree, type));
        } catch (Exception e) {
            return _describe(e);
        }
    }

    private static String _describe(HasDouble bean) {
        return "d=" + bean.d() + " (" + Long.toHexString(Double.doubleToRawLongBits(bean.d())) + ")";
    }

    private static String _describe(Exception e) {
        String desc = e.getClass().getName();
        if (e instanceof JsonMappingException) {
            JsonMappingException jme = (JsonMappingException) e;
            desc += ": " + jme.getOriginalMessage() + " at " + jme.getPathReference() + " " + jme.getLocation();
        } else {
            desc += ": " + e.getMessage();
        }
        Throwable cause = e.getCause();
        return (cause == null) ? desc : desc + ", caused by " + cause.getClass().getName() + ": " + cause.getMessage();
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
}
