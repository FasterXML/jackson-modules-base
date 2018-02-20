package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.databind.*;

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
    public void testSimple() throws Exception
    {
        ObjectMapper mapper = newMrBeanMapper();
        final String input = "{\"field\":\"testing\"}";
        final Bean bean = mapper.readValue(input, Bean.class);
        assertEquals("testing", bean.getField());
        final String output = mapper.writeValueAsString(bean);
        assertEquals(input, output);
    }

    public void testSimpleWithoutSetter() throws Exception
    {
        ObjectMapper mapper = newMrBeanMapper();
        final String input = "{\"field\":\"testing\"}";
        final ReadOnlyBean bean = mapper.readValue(input, ReadOnlyBean.class);
        assertEquals("testing", bean.getField());
        final String output = mapper.writeValueAsString(bean);
        assertEquals(input, output);
    }
}
