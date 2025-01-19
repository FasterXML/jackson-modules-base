package com.fasterxml.jackson.module.mrbean;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

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

    // for [modules-base#42]: ignore `get()` and `set()`
    public static interface JustGetAndSet {
        public int get();

        public void set(int x);

        public int foobar();
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    private final ObjectMapper MAPPER = newMrBeanMapper();

    @Test
    public void testIssue19() throws Exception
    {
        final IntegerBean integerBean = new IntegerBean();
        integerBean.setId(60);

        final String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(integerBean);
        final TypeReference<HashMap<String, Object>> typeMap = new TypeReference<HashMap<String, Object>>() {};

        final Map<String, Object> data = MAPPER.readValue(json, typeMap);
        assertEquals(Integer.valueOf(60), data.get("id"));        
    }

    // for [modules-base#42]: ignore `get()` and `set()`
    @Test
    public void testPlainGetAndSet() throws Exception
    {
        JustGetAndSet value = MAPPER.readValue("{}", JustGetAndSet.class);
        // fine to have unimplemented methods, unless...
        assertNotNull(value);

        AbstractTypeMaterializer mat = new AbstractTypeMaterializer();
        mat.enable(AbstractTypeMaterializer.Feature.FAIL_ON_UNMATERIALIZED_METHOD);
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new MrBeanModule(mat));
        try {
            mapper.readValue("{}", JustGetAndSet.class);
            fail("Should not pass");
        } catch (JsonMappingException e) {
            verifyException(e, "Unrecognized abstract method");
        }
    }
}
