package tools.jackson.module.jakarta.xmlbind.misc;

import jakarta.xml.bind.annotation.XmlRootElement;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.module.jakarta.xmlbind.ModuleTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class TestRootName extends ModuleTestBase
{
    @XmlRootElement(name="rooty")
    static class MyType
    {
        public int value = 37;
    }
    
    /*
    /**********************************************************************
    /* Unit tests
    /**********************************************************************
     */
    
    @Test
    public void testRootName() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        assertEquals("{\"rooty\":{\"value\":37}}",
                mapper.writer()
                    .with(SerializationFeature.WRAP_ROOT_VALUE)
                    .writeValueAsString(new MyType()));
    }
}
