package tools.jackson.module.jaxb.misc;

import javax.xml.bind.annotation.XmlRootElement;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.module.jaxb.BaseJaxbTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRootName extends BaseJaxbTest
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
    
    public void testRootName() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        assertEquals("{\"rooty\":{\"value\":37}}",
                mapper.writer()
                    .with(SerializationFeature.WRAP_ROOT_VALUE)
                    .writeValueAsString(new MyType()));
    }
}
