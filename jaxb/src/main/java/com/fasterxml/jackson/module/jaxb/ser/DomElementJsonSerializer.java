package com.fasterxml.jackson.module.jaxb.ser;

import java.io.IOException;

import org.w3c.dom.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;

public class DomElementJsonSerializer
    extends StdSerializer<Element>
{
    private static final long serialVersionUID = 1L;

    public DomElementJsonSerializer() { super(Element.class); }

    @Override
    public void serialize(Element value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonGenerationException
    {
        jgen.writeStartObject();
        jgen.writeStringField("name", value.getTagName());
        if (value.getNamespaceURI() != null) {
            jgen.writeStringField("namespace", value.getNamespaceURI());
        }
        NamedNodeMap attributes = value.getAttributes();
        if (attributes != null && attributes.getLength() > 0) {
            jgen.writeArrayFieldStart("attributes");
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attribute = (Attr) attributes.item(i);
                jgen.writeStartObject();
                jgen.writeStringField("$", attribute.getValue());
                jgen.writeStringField("name", attribute.getName());
                String ns = attribute.getNamespaceURI();
                if (ns != null) {
                    jgen.writeStringField("namespace", ns);
                }
                jgen.writeEndObject();
            }
            jgen.writeEndArray();
        }

        NodeList children = value.getChildNodes();
        if (children != null && children.getLength() > 0) {
            jgen.writeArrayFieldStart("children");
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                switch (child.getNodeType()) {
                    case Node.CDATA_SECTION_NODE:
                    case Node.TEXT_NODE:
                        jgen.writeStartObject();
                        jgen.writeStringField("$", child.getNodeValue());
                        jgen.writeEndObject();
                        break;
                    case Node.ELEMENT_NODE:
                        serialize((Element) child, jgen, provider);
                        break;
                }
            }
            jgen.writeEndArray();
        }
        jgen.writeEndObject();
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint)
        throws JsonMappingException
    {
        if (visitor != null) {
            visitor.expectStringFormat(typeHint);
        }
    }
}
