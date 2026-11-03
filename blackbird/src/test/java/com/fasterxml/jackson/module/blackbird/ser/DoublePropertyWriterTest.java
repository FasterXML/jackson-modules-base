package com.fasterxml.jackson.module.blackbird.ser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonTypeResolver;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.util.StdConverter;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// Primitive double getters should go through generated accessors, and write
// exactly what `BeanPropertyWriter` writes
public class DoublePropertyWriterTest extends BlackbirdTestBase
{
    static abstract class Holder {
        double value;
        int reflectiveCalls;

        double get() {
            reflectiveCalls += reflectiveCall();
            return value;
        }
    }

    static class Plain extends Holder {
        public double getD() { return get(); }
    }

    static class Always extends Holder {
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public double getD() { return get(); }
    }

    static class NonNull extends Holder {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public double getD() { return get(); }
    }

    static class NonAbsent extends Holder {
        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public double getD() { return get(); }
    }

    static class NonEmpty extends Holder {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public double getD() { return get(); }
    }

    static class NonDefault extends Holder {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        public double getD() { return get(); }
    }

    // Suppresses negative values (and only through `equals`)
    static class NegativeFilter {
        @Override
        public boolean equals(Object o) {
            return (o instanceof Double) && ((Double) o < 0.0);
        }

        @Override
        public int hashCode() { return 0; }
    }

    static class Custom extends Holder {
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = NegativeFilter.class)
        public double getD() { return get(); }
    }

    static class AsString extends Holder {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public double getD() { return get(); }
    }

    // With a class-level NON_DEFAULT, the default is the value of a new instance
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    static class DefaultZero extends Holder {
        public double getD() { return get(); }
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    static class DefaultNegativeZero extends Holder {
        public DefaultNegativeZero() { value = -0.0; }
        public double getD() { return get(); }
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    static class DefaultNaN extends Holder {
        public DefaultNaN() { value = Double.NaN; }
        public double getD() { return get(); }
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    static class DefaultOnePointFive extends Holder {
        public DefaultOnePointFive() { value = 1.5; }
        public double getD() { return get(); }
    }

    // As an array, which writes each property as an element (and a placeholder for
    // a suppressed one)
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "d", "x" })
    static class ArrayNonDefault extends Holder {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        public double getD() { return get(); }
        public int getX() { return 1; }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "d", "x" })
    static class ArrayCustom extends Holder {
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = NegativeFilter.class)
        public double getD() { return get(); }
        public int getX() { return 1; }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "d", "x" })
    static class ArrayAsString extends Holder {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public double getD() { return get(); }
        public int getX() { return 1; }
    }

    // Neither is optimized, as their serializers aren't the default
    static class Raw extends Holder {
        @JsonRawValue
        public double getD() { return get(); }
    }

    static class Negate extends StdConverter<Double, Double> {
        @Override
        public Double convert(Double value) { return -value; }
    }

    static class Converted extends Holder {
        @JsonSerialize(converter = Negate.class)
        public double getD() { return get(); }
    }

    static class NegatingSerializer extends StdSerializer<Double> {
        private static final long serialVersionUID = 1L;

        NegatingSerializer() { super(Double.class); }

        @Override
        public void serialize(Double value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeNumber(-value);
        }
    }

    // As in `TestAccessFallback`: the getter fails when called through the generated
    // accessor, and works through reflection (the fallback writer)
    static double failFromGeneratedAccessor(double value) {
        for (StackTraceElement elem : new Throwable().getStackTrace()) {
            if (elem.getClassName().equals("java.lang.reflect.Method")) {
                return value;
            }
            if (elem.getClassName().equals(DoublePropertyWriter.class.getName())) {
                throw new IllegalAccessError("simulated");
            }
        }
        return value;
    }

    static class FailingAccessor {
        public double getD() { return failFromGeneratedAccessor(2.5); }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "d", "x" })
    static class ArrayFailingAccessor {
        public double getD() { return failFromGeneratedAccessor(2.5); }
        public int getX() { return 1; }
    }

    // A resolver that gives primitives a type serializer, which `DoubleSerializer`
    // uses for non-finite values
    public static class PrimitiveTypeResolver extends StdTypeResolverBuilder {
        @Override
        protected boolean allowPrimitiveTypes(MapperConfig<?> config, JavaType baseType) {
            return true;
        }
    }

    static class Typed extends Holder {
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_ARRAY)
        @JsonTypeResolver(PrimitiveTypeResolver.class)
        public double getD() { return get(); }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    static class ArrayTyped extends Holder {
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_ARRAY)
        @JsonTypeResolver(PrimitiveTypeResolver.class)
        public double getD() { return get(); }
    }

    // Unwrapping with a prefix renames each property of the inner bean
    static class Unwrapping {
        @JsonUnwrapped(prefix = "p_")
        public Holder inner;

        Unwrapping(Holder inner) { this.inner = inner; }
    }

    private static final List<Class<? extends Holder>> TYPES = Arrays.asList(
            Plain.class, Always.class, NonNull.class, NonAbsent.class, NonEmpty.class, NonDefault.class,
            Custom.class, AsString.class, DefaultZero.class, DefaultNegativeZero.class, DefaultNaN.class,
            DefaultOnePointFive.class, ArrayNonDefault.class, ArrayCustom.class, ArrayAsString.class);

    private static final double[] VALUES = {
        0.0, -0.0, 1.5, -1.5, 0.1, 1e-7, 1e21, 1e22, 123456789.125,
        Double.NaN, Double.longBitsToDouble(0x7ff0000000000001L), Double.longBitsToDouble(0xfff8000000000000L),
        Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
        Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MIN_NORMAL
    };

    private static final List<UnaryOperator<JsonMapper.Builder>> CONFIGS = Arrays.asList(
        b -> b,
        b -> b.disable(JsonWriteFeature.WRITE_NAN_AS_STRINGS),
        b -> b.enable(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS),
        b -> b.enable(StreamWriteFeature.USE_FAST_DOUBLE_WRITER),
        b -> b.defaultPropertyInclusion(JsonInclude.Value.construct(
                JsonInclude.Include.NON_DEFAULT, JsonInclude.Include.NON_DEFAULT)),
        b -> b.defaultPropertyInclusion(JsonInclude.Value.construct(
                JsonInclude.Include.NON_EMPTY, JsonInclude.Include.NON_EMPTY)),
        b -> b.withConfigOverride(Double.TYPE, o -> o.setInclude(JsonInclude.Value.construct(
                JsonInclude.Include.NON_DEFAULT, JsonInclude.Include.NON_DEFAULT))),
        b -> b.withConfigOverride(Double.TYPE, o -> o.setInclude(JsonInclude.Value.construct(
                JsonInclude.Include.CUSTOM, JsonInclude.Include.ALWAYS, NegativeFilter.class, null))),
        b -> b.withConfigOverride(Double.TYPE, o -> o.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING))),
        b -> b.addModule(new SimpleModule().addSerializer(Double.TYPE, new NegatingSerializer()))
    );

    // Whatever called the getter: reflection, or the generated accessor (whose frame
    // is hidden, so its caller shows instead)
    static int reflectiveCall() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (int i = 0; i < stack.length - 1; i++) {
            if (stack[i].getMethodName().equals("getD")) {
                String caller = stack[i + 1].getClassName();
                return (caller.startsWith("java.lang.reflect.") || caller.startsWith("jdk.internal.reflect.")
                        || caller.startsWith("sun.reflect.")) ? 1 : 0;
            }
        }
        throw new AssertionError("not called from a getter");
    }

    private final ObjectMapper MAPPER = newObjectMapper();

    private static <T extends Holder> T holder(Class<T> type, double value) throws Exception {
        T holder = type.getDeclaredConstructor().newInstance();
        holder.value = value;
        return holder;
    }

    @Test
    public void testUsesGeneratedGetter() throws Exception
    {
        for (Class<? extends Holder> type : TYPES) {
            assertEquals(DoublePropertyWriter.class, _findWriter(MAPPER, type, "d").getClass(), type.getSimpleName());
        }
        Holder bean = holder(Plain.class, 2.5);
        assertEquals(a2q("{'d':2.5}"), MAPPER.writeValueAsString(bean));
        assertEquals(0, bean.reflectiveCalls);
        bean = holder(ArrayNonDefault.class, 2.5);
        assertEquals("[2.5,1]", MAPPER.writeValueAsString(bean));
        assertEquals(0, bean.reflectiveCalls);
        // Sanity check that the reflection probe works at all
        bean = holder(Plain.class, 2.5);
        assertEquals(a2q("{'d':2.5}"), newVanillaJSONMapper().writeValueAsString(bean));
        assertEquals(1, bean.reflectiveCalls);
    }

    @Test
    public void testOutputMatchesVanilla() throws Exception
    {
        for (int i = 0; i < CONFIGS.size(); i++) {
            ObjectMapper vanilla = CONFIGS.get(i).apply(JsonMapper.builder()).build();
            ObjectMapper blackbird = CONFIGS.get(i).apply(mapperBuilder()).build();
            List<Class<? extends Holder>> types = new ArrayList<>(TYPES);
            types.add(Raw.class);
            types.add(Converted.class);
            types.add(Typed.class);
            types.add(ArrayTyped.class);
            for (Class<? extends Holder> type : types) {
                for (double value : VALUES) {
                    String desc = "config " + i + ", " + type.getSimpleName() + ", " + value
                            + " (" + Long.toHexString(Double.doubleToRawLongBits(value)) + ")";
                    assertEquals(_write(vanilla, holder(type, value)), _write(blackbird, holder(type, value)), desc);
                    // and through a buffer
                    assertEquals(_toTree(vanilla, holder(type, value)), _toTree(blackbird, holder(type, value)), desc);
                }
            }
        }
    }

    @Test
    public void testUnwrappedMatchesVanilla() throws Exception
    {
        ObjectMapper vanilla = newVanillaJSONMapper();
        for (Class<? extends Holder> type : TYPES) {
            if (type.getSimpleName().startsWith("Array")) {
                continue;
            }
            for (double value : VALUES) {
                String desc = type.getSimpleName() + ", " + value;
                assertEquals(vanilla.writeValueAsString(new Unwrapping(holder(type, value))),
                        MAPPER.writeValueAsString(new Unwrapping(holder(type, value))), desc);
            }
        }
        assertEquals("{}", MAPPER.writeValueAsString(new Unwrapping(holder(Custom.class, -1.5))));
        assertEquals(a2q("{'p_d':-0.0}"), MAPPER.writeValueAsString(new Unwrapping(holder(NonDefault.class, -0.0))));
    }

    // The cases decided only through the generated getter, on the values where
    // `!=` and `Double.equals` disagree
    @Test
    public void testSuppressionComparesAsDoubleEquals() throws Exception
    {
        assertEquals(a2q("{'d':-0.0}"), MAPPER.writeValueAsString(holder(NonDefault.class, -0.0)));
        assertEquals("{}", MAPPER.writeValueAsString(holder(NonDefault.class, 0.0)));
        assertEquals(a2q("{'d':0.0}"), MAPPER.writeValueAsString(holder(DefaultNegativeZero.class, 0.0)));
        assertEquals("{}", MAPPER.writeValueAsString(holder(DefaultNegativeZero.class, -0.0)));
        assertEquals("{}", MAPPER.writeValueAsString(holder(DefaultNaN.class, Double.NaN)));
        assertEquals("{}", MAPPER.writeValueAsString(
                holder(DefaultNaN.class, Double.longBitsToDouble(0x7ff0000000000001L))));
        assertEquals(a2q("{'d':0.0}"), MAPPER.writeValueAsString(holder(DefaultNaN.class, 0.0)));
        assertEquals("[null,1]", MAPPER.writeValueAsString(holder(ArrayNonDefault.class, 0.0)));
        assertEquals("[-0.0,1]", MAPPER.writeValueAsString(holder(ArrayNonDefault.class, -0.0)));
        // and NON_EMPTY suppresses nothing
        assertEquals(a2q("{'d':0.0}"), MAPPER.writeValueAsString(holder(NonEmpty.class, 0.0)));
    }

    @Test
    public void testCustomFilter() throws Exception
    {
        assertEquals("{}", MAPPER.writeValueAsString(holder(Custom.class, -1.5)));
        assertEquals(a2q("{'d':1.5}"), MAPPER.writeValueAsString(holder(Custom.class, 1.5)));
        assertEquals("[null,1]", MAPPER.writeValueAsString(holder(ArrayCustom.class, -1.5)));
        assertEquals("[1.5,1]", MAPPER.writeValueAsString(holder(ArrayCustom.class, 1.5)));
    }

    // [modules-base#327]
    @Test
    public void testStringShape() throws Exception
    {
        assertEquals(a2q("{'d':'1.5'}"), MAPPER.writeValueAsString(holder(AsString.class, 1.5)));
        assertEquals(a2q("{'d':'NaN'}"), MAPPER.writeValueAsString(holder(AsString.class, Double.NaN)));
        assertEquals("[\"1.5\",1]", MAPPER.writeValueAsString(holder(ArrayAsString.class, 1.5)));
        ObjectMapper overridden = mapperBuilder()
                .withConfigOverride(Double.TYPE, o -> o.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)))
                .build();
        assertEquals(a2q("{'d':'1.5'}"), overridden.writeValueAsString(holder(Plain.class, 1.5)));
    }

    @Test
    public void testTypedNonFiniteValues() throws Exception
    {
        assertEquals(a2q("{'d':['java.lang.Double','NaN']}"), MAPPER.writeValueAsString(holder(Typed.class, Double.NaN)));
        assertEquals(a2q("{'d':1.5}"), MAPPER.writeValueAsString(holder(Typed.class, 1.5)));
        assertEquals(a2q("[['java.lang.Double','Infinity']]"),
                MAPPER.writeValueAsString(holder(ArrayTyped.class, Double.POSITIVE_INFINITY)));
    }

    @Test
    public void testNonFiniteValues() throws Exception
    {
        assertEquals(a2q("{'d':'NaN'}"), MAPPER.writeValueAsString(holder(Plain.class, Double.NaN)));
        assertEquals(a2q("{'d':'-Infinity'}"), MAPPER.writeValueAsString(holder(Plain.class, Double.NEGATIVE_INFINITY)));
        ObjectMapper raw = mapperBuilder().disable(JsonWriteFeature.WRITE_NAN_AS_STRINGS).build();
        assertEquals(a2q("{'d':NaN}"), raw.writeValueAsString(holder(Plain.class, Double.NaN)));
        assertEquals(a2q("{'d':Infinity}"), raw.writeValueAsString(holder(Plain.class, Double.POSITIVE_INFINITY)));
    }

    // An access error falls back to reflection, as a field or as an element
    @Test
    public void testAccessErrorFallsBack() throws Exception
    {
        assertEquals(DoublePropertyWriter.class, _findWriter(MAPPER, FailingAccessor.class, "d").getClass());
        assertEquals(DoublePropertyWriter.class, _findWriter(MAPPER, ArrayFailingAccessor.class, "d").getClass());
        assertEquals(a2q("{'d':2.5}"), newObjectMapper().writeValueAsString(new FailingAccessor()));
        assertEquals("[2.5,1]", newObjectMapper().writeValueAsString(new ArrayFailingAccessor()));
    }

    // The output, or the failure (as with a custom serializer that can't write a type id)
    private static String _write(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    private static String _toTree(ObjectMapper mapper, Object value) {
        try {
            return String.valueOf(mapper.valueToTree(value));
        } catch (Exception e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    private static PropertyWriter _findWriter(ObjectMapper mapper, Class<?> type, String name) throws Exception
    {
        BeanSerializerBase ser = (BeanSerializerBase) mapper.getSerializerProviderInstance()
                .findValueSerializer(type);
        for (Iterator<PropertyWriter> it = ser.properties(); it.hasNext(); ) {
            PropertyWriter writer = it.next();
            if (writer.getName().equals(name)) {
                return writer;
            }
        }
        throw new AssertionError("No property '" + name + "' for " + type.getName());
    }
}
