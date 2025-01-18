package tools.jackson.module.afterburner.deser.convert;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestFailOnPrimitiveFromNullDeserialization
    extends AfterburnerTestBase
{
    static class LongBean
    {
        public long value;
    }

    static class IntBean
    {
        public int value;
    }

    static class BooleanBean
    {
        public boolean value;
    }

    static class DoubleBean
    {
        public double value;
    }

    private final static String BEAN_WITH_NULL_VALUE = "{\"value\": null}";

    private final ObjectMapper MAPPER = afterburnerMapperBuilder()
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .build();
    private final ObjectMapper FAIL_ON_NULL_MAPPER = afterburnerMapperBuilder()
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .build();

    @Test
    public void testPassPrimitiveFromNull() throws Exception
    {
        LongBean longBean = MAPPER.readValue(BEAN_WITH_NULL_VALUE, LongBean.class);
        IntBean intBean = MAPPER.readValue(BEAN_WITH_NULL_VALUE, IntBean.class);
        BooleanBean booleanBean = MAPPER.readValue(BEAN_WITH_NULL_VALUE, BooleanBean.class);
        DoubleBean doubleBean = MAPPER.readValue(BEAN_WITH_NULL_VALUE, DoubleBean.class);
        assertEquals(longBean.value, 0);
        assertEquals(intBean.value, 0);
        assertEquals(booleanBean.value, false);
        assertEquals(doubleBean.value, 0.0);
    }

    @Test
    public void testFailPrimitiveFromNull() throws Exception
    {
        try {
            FAIL_ON_NULL_MAPPER.readValue(BEAN_WITH_NULL_VALUE, IntBean.class);
            fail();
        } catch (MismatchedInputException e) {
//            verifyException(e, "Cannot coerce `null` to `int` value");
            verifyException(e, "Cannot map `null` into type `int`");
        }
        try {
            FAIL_ON_NULL_MAPPER.readValue(BEAN_WITH_NULL_VALUE, LongBean.class);
            fail();
        } catch (MismatchedInputException e) {
//            verifyException(e, "Cannot coerce `null` to `long` value");
            verifyException(e, "Cannot map `null` into type `long`");
        }
        try {
            FAIL_ON_NULL_MAPPER.readValue(BEAN_WITH_NULL_VALUE, BooleanBean.class);
            fail();
        } catch (MismatchedInputException e) {
//            verifyException(e, "Cannot coerce `null` to `boolean` value");
            verifyException(e, "Cannot map `null` into type `boolean`");
        }
        try {
            FAIL_ON_NULL_MAPPER.readValue(BEAN_WITH_NULL_VALUE, DoubleBean.class);
            fail();
        } catch (MismatchedInputException e) {
            verifyException(e, "Cannot map `null` into type `double`");
        }
    }
}
