package com.fasterxml.jackson.module.jaxb.tofix;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;
import com.fasterxml.jackson.module.jaxb.testutil.failure.JacksonTestFailureExpected;

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
    @JacksonTestFailureExpected
    @Test
    public void testEnumSerialize256() throws Exception
    {
        final Document256 document = new Document256(Code.RED);

        String json = MAPPER.writeValueAsString(document);

        assertEquals(a2q("{'code':'RED'}"), json);
    }
}
