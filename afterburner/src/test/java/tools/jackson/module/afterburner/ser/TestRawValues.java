package tools.jackson.module.afterburner.ser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonRawValue;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestRawValues extends AfterburnerTestBase
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

    private final ObjectMapper MAPPER = newAfterburnerMapper();

    @Test
    public void testAfterBurner() throws Exception
    {
        SerializableObject so = new SerializableObject("[123]");

        assertEquals("{\"value\":[123]}", MAPPER.writeValueAsString(so));
    }    
}
