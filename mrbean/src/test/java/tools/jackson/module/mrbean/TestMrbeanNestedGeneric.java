package tools.jackson.module.mrbean;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.*;

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
        final ObjectMapper mapper = newMrBeanMapper();
        final String JSON = "{\"value\":{\"breed\":\"Poodle\",\"name\":\"Rekku\"}}";

        final ResultWrapper<Dog> result = mapper.readValue(JSON, 
                new TypeReference<ResultWrapper<Dog>>() { });
        Object ob = result.getValue();
        assertTrue(ob instanceof Dog);
    }

    @Test
    public void testTypeReferenceNestedGenericList() throws Exception
    {
        final ObjectMapper mapper = newMrBeanMapper();
        final String JSON = "{\"records\":[{\"breed\":\"Mountain Cur\",\"name\":\"Fido\"}],\n"
            +"\"total\":1}";
        final Results<Dog> result = mapper.readValue(JSON, new TypeReference<Results<Dog>>() { });

        List<?> records = result.getRecords();
        assertEquals(1, records.size());
        Object ob = records.get(0);
        assertTrue(ob instanceof Dog, "Actual type: "+ob.getClass());
    }
}
