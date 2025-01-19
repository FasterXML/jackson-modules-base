package tools.jackson.module.jakarta.xmlbind.misc;

import org.junit.jupiter.api.Test;

import jakarta.xml.bind.annotation.XmlEnum;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.jakarta.xmlbind.ModuleTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestEnums extends ModuleTestBase
{
    enum Plain { A, B; }

    @XmlEnum(Integer.class)
    enum Numeric { A, B; }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    @Test
    public void testWrapperWithCollection() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        assertEquals("\"B\"", mapper.writeValueAsString(Plain.B));
        assertEquals("1", mapper.writeValueAsString(Numeric.B));
    }
}
