package com.fasterxml.jackson.module.mrbean;

import java.util.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestSimpleTypes extends BaseTest
{
    // for [mrbean#19]:

    static class IntegerBean implements java.io.Serializable
    {
        private static final long serialVersionUID = 1L;
        private Integer id;

        public IntegerBean() { }

        public Integer getId() { return id; }
        public void setId(final Integer pId) { id = pId; }
    }    

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    private final ObjectMapper MAPPER = newMrBeanMapper();

    public void testIssue19() throws Exception
    {
        final IntegerBean integerBean = new IntegerBean();
        integerBean.setId(60);

        final String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(integerBean);
        final TypeReference<HashMap<String, Object>> typeMap = new TypeReference<HashMap<String, Object>>() {};

        final Map<String, Object> data = MAPPER.readValue(json, typeMap);
        assertEquals(Integer.valueOf(60), data.get("id"));        
    }
}
