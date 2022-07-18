package tools.jackson.module.blackbird.ser;

import com.fasterxml.jackson.annotation.JsonRawValue;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

public class TestRawValues extends BlackbirdTestBase
{
    static class SerializableObject
    {
        public SerializableObject(String v) { value = v; }
        
        @JsonRawValue
        public String value;
    }    

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    private final ObjectMapper MAPPER = newObjectMapper();

    public void testAfterBurner() throws Exception
    {
        SerializableObject so = new SerializableObject("[123]");

        assertEquals("{\"value\":[123]}", MAPPER.writeValueAsString(so));
    }    
}
