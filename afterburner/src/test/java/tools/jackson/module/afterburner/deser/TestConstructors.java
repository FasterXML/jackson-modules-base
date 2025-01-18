package tools.jackson.module.afterburner.deser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestConstructors extends AfterburnerTestBase
{
    // [Issue#34]
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class Row
    {
        private String id;

        public String _id() { return id; }
    }
    
    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    // For [Issue#34]
    @Test
    public void testPrivateConstructor() throws Exception
    {
        ObjectMapper mapper = newAfterburnerMapper();
        Row row = mapper.readValue("{\"id\":\"x\"}", Row.class);
        assertNotNull(row);
        assertEquals("x", row._id());
        
    }
}
