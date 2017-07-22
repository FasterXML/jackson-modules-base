package com.fasterxml.jackson.module.jaxb.deser;

import java.io.IOException;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * @author Ryan Heaton
 */
public class DomElementJsonDeserializer
    extends StdDeserializer<Element>
{
    private static final long serialVersionUID = 1L;

    private final DocumentBuilder builder;

    public DomElementJsonDeserializer()
    {
        super(Element.class);
        try {
            DocumentBuilderFactory bf = DocumentBuilderFactory.newInstance();
            bf.setNamespaceAware(true);
            builder = bf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException();
        }
    }

    public DomElementJsonDeserializer(DocumentBuilder builder)
    {
        super(Element.class);
        this.builder = builder;
    }

    @Override
    public Element deserialize(JsonParser p, DeserializationContext ctxt) throws IOException
    {
        Document document = builder.newDocument();
        JsonNode n = p.readValueAsTree();
        return fromNode(p, document, n);
    }

    protected Element fromNode(JsonParser p, Document document, JsonNode jsonNode)
        throws IOException
    {
        String ns = jsonNode.get("namespace") != null ? jsonNode.get("namespace").asText() : null;
        String name = jsonNode.get("name") != null ? jsonNode.get("name").asText() : null;
        if (name == null) {
            throw JsonMappingException.from(p, "No name for DOM element was provided in the JSON object.");
        }
        Element element = document.createElementNS(ns, name);

        JsonNode attributesNode = jsonNode.get("attributes");
        if (attributesNode != null && attributesNode instanceof ArrayNode) {
            Iterator<JsonNode> atts = attributesNode.elements();
            while (atts.hasNext()) {
                JsonNode node = atts.next();
                ns = node.get("namespace") != null ? node.get("namespace").asText() : null;
                name = node.get("name") != null ? node.get("name").asText() : null;
                String value = node.get("$") != null ? node.get("$").asText() : null;

                if (name != null) {
                    element.setAttributeNS(ns, name, value);
                }
            }
        }

        JsonNode childsNode = jsonNode.get("children");
        if (childsNode != null && childsNode instanceof ArrayNode) {
            Iterator<JsonNode> els = childsNode.elements();
            while (els.hasNext()) {
                JsonNode node = els.next();
                name = node.get("name") != null ? node.get("name").asText() : null;
                String value = node.get("$") != null ? node.get("$").asText() : null;

                if (value != null) {
                    element.appendChild(document.createTextNode(value));
                }
                else if (name != null) {
                    element.appendChild(fromNode(p, document, node));
                }
            }
        }

        return element;
    }
}
