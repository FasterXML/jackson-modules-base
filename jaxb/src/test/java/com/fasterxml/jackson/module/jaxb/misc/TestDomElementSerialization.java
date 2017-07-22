package com.fasterxml.jackson.module.jaxb.misc;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.Serializers;

import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;
import com.fasterxml.jackson.module.jaxb.deser.DomElementJsonDeserializer;
import com.fasterxml.jackson.module.jaxb.ser.DomElementJsonSerializer;

/**
 * @author Ryan Heaton
 */
public class TestDomElementSerialization extends BaseJaxbTest
{
    /*
    /**********************************************************
    /* Helper classes
    /**********************************************************
     */

    @SuppressWarnings("serial")
    private final static class DomModule extends SimpleModule
    {
        public DomModule()
        {
            super("DomModule", Version.unknownVersion());
            addDeserializer(Element.class, new DomElementJsonDeserializer());
            /* 19-Feb-2011, tatu: Note: since SimpleModule does not support "generic"
             *   serializers, need to add bit more code here.
             */
            //testModule.addSerializer(new DomElementJsonSerializer());
        }

        @Override
        public void setupModule(SetupContext context)
        {
            super.setupModule(context);
            context.addSerializers(new DomSerializers());
        }
    }
    
    private final static class DomSerializers extends Serializers.Base
    {
        @Override
        public JsonSerializer<?> findSerializer(SerializationConfig config,
                JavaType type, BeanDescription beanDesc)
        {
            if (Element.class.isAssignableFrom(type.getRawClass())) {
                return new DomElementJsonSerializer();
            }
            return null;
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */
    
    public void testBasicDomElementSerializationDeserialization() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new DomModule());

        StringBuilder builder = new StringBuilder()
                .append("<document xmlns=\"urn:hello\" att1=\"value1\" att2=\"value2\">")
                .append("<childel>howdy</childel>")
                .append("</document>");

        DocumentBuilderFactory bf = DocumentBuilderFactory.newInstance();
        bf.setNamespaceAware(true);
        Document document = bf.newDocumentBuilder().parse(new ByteArrayInputStream(builder.toString().getBytes("utf-8")));
        StringWriter jsonElement = new StringWriter();
        mapper.writeValue(jsonElement, document.getDocumentElement());

        Element el = mapper.readValue(jsonElement.toString(), Element.class);
        assertEquals(3, el.getAttributes().getLength());
        assertEquals("value1", el.getAttributeNS(null, "att1"));
        assertEquals("value2", el.getAttributeNS(null, "att2"));
        assertEquals(1, el.getChildNodes().getLength());
        assertEquals("childel", el.getChildNodes().item(0).getLocalName());
        assertEquals("urn:hello", el.getChildNodes().item(0).getNamespaceURI());
        assertEquals("howdy", el.getChildNodes().item(0).getTextContent());
    }
}
