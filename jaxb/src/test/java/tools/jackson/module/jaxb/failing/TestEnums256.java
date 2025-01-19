package tools.jackson.module.jaxb.failing;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.*;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.jaxb.BaseJaxbTest;

import static org.junit.jupiter.api.Assertions.*;

public class TestEnums256 extends BaseJaxbTest
{
    // [modules-base#256]
    @XmlRootElement(name = "document")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Document256 {

      @XmlElement(name = "code")
      private final Code _code;

      public Document256(Code code) { _code = code; }

      public Code getCode() {
        return _code;
      }
    }

    @XmlEnum
    @XmlType(name = "CodeType")
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    public enum Code {
        @XmlEnumValue("RED")
        RED;
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = getJaxbMapper();

    // [modules-base#256]
    @Test
    public void testEnumSerialize256() throws Exception
    {
        final Document256 document = new Document256(Code.RED);

        String json = MAPPER.writeValueAsString(document);

        assertEquals(a2q("{'code':'RED'}"), json);
    }
}
