package com.fasterxml.jackson.module.jaxb.introspect;

import java.io.*;
import java.math.BigDecimal;
import java.util.Map;

import javax.xml.bind.annotation.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.type.TypeFactory;

import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for verifying auto-detection settings with JAXB annotations.
 *
 * @author Tatu Saloranta
 */
public class TestJaxbAutoDetect extends BaseJaxbTest
{
    /*
    /**********************************************************
    /* Helper beans
    /**********************************************************
     */

    /* Bean for testing problem [JACKSON-183]: with normal
     * auto-detect enabled, 2 fields visible; if disabled, just 1.
     * NOTE: should NOT include "XmlAccessorType", since it will
     * have priority over global defaults
     */
    static class Jackson183Bean {
        public String getA() { return "a"; }

        @XmlElement public String getB() { return "b"; }

        // JAXB (or Bean introspection) mandates use of matching setters...
        public void setA(String str) { }
        public void setB(String str) { }
    }

    static class Identified
    {
        Object id;
        
        @XmlAttribute(name="id")
        public Object getIdObject() {
            return id;
        }
        public void setId(Object id) { this.id = id; }
    }

    @XmlRootElement(name="bah")
    public static class JaxbAnnotatedObject {
        BigDecimal number;

        public JaxbAnnotatedObject() { }
        
        public JaxbAnnotatedObject(String number) {
            this.number = new BigDecimal(number);
        }

        @XmlElement
        public void setNumber(BigDecimal number) {
            this.number = number;
        }

        @XmlTransient
        public BigDecimal getNumber() {
            return number;
        }

        @XmlElement(name = "number")
        public BigDecimal getNumberString() {
            return number;
        }
    }

    @SuppressWarnings("serial")
    public static class DualAnnotationObjectMapper extends ObjectMapper {

        public DualAnnotationObjectMapper() {
            super();
            AnnotationIntrospector primary = new JaxbAnnotationIntrospector(TypeFactory.defaultInstance());
            AnnotationIntrospector secondary = new JacksonAnnotationIntrospector();

            // make de/serializer use JAXB annotations first, then jackson ones
            AnnotationIntrospector pair = new AnnotationIntrospectorPair(primary, secondary);
            setAnnotationIntrospector(pair);
        }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    @Test
    public void testAutoDetectDisable() throws IOException
    {
        ObjectMapper mapper = getJaxbMapper();
        Jackson183Bean bean = new Jackson183Bean();
        Map<String,Object> result;

        // Ok: by default, should see 2 fields:
        result = writeAndMap(mapper, bean);
        assertEquals(2, result.size());
        assertEquals("a", result.get("a"));
        assertEquals("b", result.get("b"));

        // But when disabling auto-detection, just one
        mapper = getJaxbMapperBuilder()
              .configure(MapperFeature.AUTO_DETECT_GETTERS, false)
              .build();
        result = writeAndMap(mapper, bean);
        assertEquals(1, result.size());
        assertNull(result.get("a"));
        assertEquals("b", result.get("b"));
    }

    @Test
    public void testIssue246() throws IOException
    {
        ObjectMapper mapper = getJaxbMapper();
        Identified id = new Identified();
        id.id = "123";
        assertEquals("{\"id\":\"123\"}", mapper.writeValueAsString(id));
    }

    // [JACKSON-556]
    @Test
    public void testJaxbAnnotatedObject() throws Exception
    {
        JaxbAnnotatedObject original = new JaxbAnnotatedObject("123");
        ObjectMapper mapper = new DualAnnotationObjectMapper();
        String json = mapper.writeValueAsString(original);
        assertFalse(json.contains("numberString"), "numberString field in JSON"); // kinda hack-y :)
        JaxbAnnotatedObject result = mapper.readValue(json, JaxbAnnotatedObject.class);
        assertEquals(new BigDecimal("123"), result.number);
    }

    /*
    @Test
    public void testJaxbAnnotatedObjectXML() throws Exception
    {
        JAXBContext ctxt = JAXBContext.newInstance(JaxbAnnotatedObject.class);
        JaxbAnnotatedObject original = new JaxbAnnotatedObject("123");
        StringWriter sw = new StringWriter();
        ctxt.createMarshaller().marshal(original, sw);
        String xml = sw.toString();
        JaxbAnnotatedObject result = (JaxbAnnotatedObject) ctxt.createUnmarshaller().unmarshal(new StringReader(xml));
        assertEquals(new BigDecimal("123"), result.number);
    }
    */
}
