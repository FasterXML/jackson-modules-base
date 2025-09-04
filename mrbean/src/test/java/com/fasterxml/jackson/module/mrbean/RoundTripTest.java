package com.fasterxml.jackson.module.mrbean;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.*;

import static org.junit.jupiter.api.Assertions.*;

public class RoundTripTest extends BaseTest
{
    public interface Bean {
        String getField();
        void setField(String field);
    }

    public interface ReadOnlyBean {
        String getField();
    }

    // [mrbean#20]: naming convention caused under-score prefixed duplicates
    @Test
    public void testSimple() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new MrBeanModule());
        final String input = "{\"field\":\"testing\"}";
        final Bean bean = mapper.readValue(input, Bean.class);
        assertEquals("testing", bean.getField());
        final String output = mapper.writeValueAsString(bean);
        assertEquals(input, output);
    }

    @Test
    public void testSimpleWithoutSetter() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new MrBeanModule());
        final String input = "{\"field\":\"testing\"}";
        final ReadOnlyBean bean = mapper.readValue(input, ReadOnlyBean.class);
        assertEquals("testing", bean.getField());
        final String output = mapper.writeValueAsString(bean);
        assertEquals(input, output);
    }
}
