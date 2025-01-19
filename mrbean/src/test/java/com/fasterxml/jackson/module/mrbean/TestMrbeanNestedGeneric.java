package com.fasterxml.jackson.module.mrbean;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify whether generic declarations are properly handled by Mr Bean.
 */
public class TestMrbeanNestedGeneric extends BaseTest
{
    public interface ResultWrapper<T> {
        T getValue();
    }

    public interface Results<T> {
        Long getTotal();
        List<T> getRecords();
    }
    public interface Dog {
        String getName();
        String getBreed();
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    @Test
    public void testTypeReferenceNestedGeneric() throws Exception
    {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MrBeanModule());
        final String JSON = "{\"value\":{\"breed\":\"Poodle\",\"name\":\"Rekku\"}}";

        final ResultWrapper<Dog> result = mapper.readValue(JSON, 
                new TypeReference<ResultWrapper<Dog>>() { });
        Object ob = result.getValue();
        assertTrue(ob instanceof Dog);
    }

    @Test
    public void testTypeReferenceNestedGenericList() throws Exception
    {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MrBeanModule());

        final String JSON = "{\"records\":[{\"breed\":\"Mountain Cur\",\"name\":\"Fido\"}],\n"
            +"\"total\":1}";

        JavaType type = mapper.getTypeFactory().constructType(new TypeReference<Results<Dog>>() { });
        Results<Dog> result = mapper.readValue(JSON, type);

        List<?> records = result.getRecords();
        assertEquals(1, records.size());
        Object ob = records.get(0);
        assertTrue(ob instanceof Dog, "Actual type: "+ob.getClass());
    }
}
