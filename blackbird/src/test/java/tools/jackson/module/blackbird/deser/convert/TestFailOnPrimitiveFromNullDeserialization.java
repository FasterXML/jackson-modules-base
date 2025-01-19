package tools.jackson.module.blackbird.deser.convert;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestFailOnPrimitiveFromNullDeserialization extends BlackbirdTestBase
{
    static class LongBean
    {
        public long value;

        public void setValue(long v) { value = v; }
    }

    static class IntBean
    {
        public int value;

        public void setValue(int v) { value = v; }
    }

    static class BooleanBean
    {
        public boolean value;

        public void setValue(boolean v) { value = v; }
    }

    static class DoubleBean
    {
        public double value;

        public void setValue(double v) { value = v; }
    }

    private final static String BEAN_WITH_NULL_VALUE = "{\"value\": null}";

    private final ObjectMapper MAPPER = newObjectMapper();
    private final ObjectMapper FAIL_ON_NULL_MAPPER = mapperBuilder()
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    @Test
    public void testPassPrimitiveFromNull() throws Exception
    {
        ObjectMapper mapper = mapperBuilder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

        LongBean longBean = mapper.readValue(BEAN_WITH_NULL_VALUE, LongBean.class);
        IntBean intBean = mapper.readValue(BEAN_WITH_NULL_VALUE, IntBean.class);
        BooleanBean booleanBean = mapper.readValue(BEAN_WITH_NULL_VALUE, BooleanBean.class);
        DoubleBean doubleBean = mapper.readValue(BEAN_WITH_NULL_VALUE, DoubleBean.class);
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
            verifyException(e, "Cannot map `null` into type `int`");
        }
        try {
            FAIL_ON_NULL_MAPPER.readValue(BEAN_WITH_NULL_VALUE, LongBean.class);
            fail();
        } catch (MismatchedInputException e) {
            verifyException(e, "Cannot map `null` into type `long`");
        }
        try {
            FAIL_ON_NULL_MAPPER.readValue(BEAN_WITH_NULL_VALUE, BooleanBean.class);
            fail();
        } catch (MismatchedInputException e) {
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
