package com.fasterxml.jackson.module.jaxb.introspect;

import java.util.*;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedClassResolver;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.type.TypeFactory;

import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

/**
 * Tests for verifying that JAXB annotation based introspector
 * implementation works as expected
 *
 * @author Ryan Heaton
 * @author Tatu Saloranta
 */
public class TestJaxbAnnotationIntrospector
    extends BaseJaxbTest
{
    /*
    /****************************************************
    /* Helper beans
    /****************************************************
     */

    public static enum EnumExample {

        @XmlEnumValue("Value One")
        VALUE1
    }

    public static class JaxbExample
    {
        protected String attributeProperty;
        protected String elementProperty;
        protected List<String> wrappedElementProperty;
        protected EnumExample enumProperty;
        protected QName qname;
        protected QName qname1;
        protected String propertyToIgnore;

        @XmlJavaTypeAdapter(QNameAdapter.class)
        public QName getQname()
        {
            return qname;
        }

        public void setQname(QName qname)
        {
            this.qname = qname;
        }

        public QName getQname1()
        {
            return qname1;
        }

        public void setQname1(QName qname1)
        {
            this.qname1 = qname1;
        }

        @XmlAttribute(name="myattribute")
        public String getAttributeProperty()
        {
            return attributeProperty;
        }

        public void setAttributeProperty(String attributeProperty)
        {
            this.attributeProperty = attributeProperty;
        }

        @XmlElement(name="myelement")
        public String getElementProperty()
        {
            return elementProperty;
        }

        public void setElementProperty(String elementProperty)
        {
            this.elementProperty = elementProperty;
        }

        @XmlElementWrapper(name="mywrapped")
        public List<String> getWrappedElementProperty()
        {
            return wrappedElementProperty;
        }

        public void setWrappedElementProperty(List<String> wrappedElementProperty)
        {
            this.wrappedElementProperty = wrappedElementProperty;
        }

        public EnumExample getEnumProperty()
        {
            return enumProperty;
        }

        public void setEnumProperty(EnumExample enumProperty)
        {
            this.enumProperty = enumProperty;
        }

        @XmlTransient
        public String getPropertyToIgnore()
        {
            return propertyToIgnore;
        }

        public void setPropertyToIgnore(String propertyToIgnore)
        {
            this.propertyToIgnore = propertyToIgnore;
        }
    }

    public static class QNameAdapter extends XmlAdapter<String, QName> {

        @Override
        public QName unmarshal(String v) throws Exception
        {
            return QName.valueOf(v);
        }

        @Override
        public String marshal(QName v) throws Exception
        {
            return (v == null) ? null : v.toString();
        }
    }

    @XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
    public static class SimpleBean
    {
        @XmlElement
        protected String jaxb = "1";

        @XmlElement
        protected String jaxb2 = "2";

        @XmlElement(name="jaxb3")
        private String oddName = "3";

        public String notAGetter() { return "xyz"; }

        @XmlTransient
        public int foobar = 3;
    }

    @SuppressWarnings("unused")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SimpleBean2 {

        protected String jaxb = "1";
        private String jaxb2 = "2";
        @XmlElement(name="jaxb3")
        private String oddName = "3";

    }

    @XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
    @XmlRootElement(namespace="urn:class")
    static class NamespaceBean
    {
        @XmlElement(namespace="urn:method")
        public String string;
    }

    @XmlRootElement(name="test")
    static class RootNameBean { }

    @XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
    public static class AlphaBean
    {
        public int c = 3;
        public int a = 1;
        public int b = 2;
    }
    
    public static class KeyValuePair {
    	private String key;
    	private String value;
    	public KeyValuePair() {}
    	public String getKey() {
    	    return key;
    	}
    	public void setKey(String key) {
    	    this.key = key;
    	}
    	public String getValue() {
    	    return value;
    	}
    	public void setValue(String value) {
    	    this.value = value;
    	}
    }

    /*
    /****************************************************
    /* Unit tests
    /****************************************************
     */

    private final ObjectMapper MAPPER = getJaxbMapper();
    
    public void testDetection() throws Exception
    {
        Map<String,Object> result = writeAndMap(MAPPER, new SimpleBean());
        assertEquals(3, result.size());
        assertEquals("1", result.get("jaxb"));
        assertEquals("2", result.get("jaxb2"));
        assertEquals("3", result.get("jaxb3"));

        result = writeAndMap(MAPPER, new SimpleBean2());
        assertEquals(3, result.size());
        assertEquals("1", result.get("jaxb"));
        assertEquals("2", result.get("jaxb2"));
        assertEquals("3", result.get("jaxb3"));
    }

    /**
     * tests getting serializer/deserializer instances.
     */
    public void testSerializeDeserializeWithJaxbAnnotations() throws Exception
    {
        ObjectMapper mapper = getJaxbMapperBuilder()
                // test expects that wrapper name be used...
                .enable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME)
                .build();
        
        JaxbExample ex = new JaxbExample();
        QName qname = new QName("urn:hi", "hello");
        ex.setQname(qname);
        QName qname1 = new QName("urn:hi", "hello1");
        ex.setQname1(qname1);
        ex.setAttributeProperty("attributeValue");
        ex.setElementProperty("elementValue");
        ex.setWrappedElementProperty(Arrays.asList("wrappedElementValue"));
        ex.setEnumProperty(EnumExample.VALUE1);
        ex.setPropertyToIgnore("ignored");
        String json = mapper.writeValueAsString(ex);

        // uncomment to see what the JSON looks like.
        // System.out.println(json);
        
        //make sure the json is written out correctly.
        JsonNode node = mapper.readValue(json, JsonNode.class);
        assertEquals(qname.toString(), node.get("qname").asText());
        JsonNode attr = node.get("myattribute");
        assertNotNull(attr);
        assertEquals("attributeValue", attr.asText());
        assertEquals("elementValue", node.get("myelement").asText());
        assertTrue(node.has("mywrapped"));
        assertEquals(1, node.get("mywrapped").size());
        assertEquals("wrappedElementValue", node.get("mywrapped").get(0).asText());
        assertEquals("Value One", node.get("enumProperty").asText());
        assertNull(node.get("propertyToIgnore"));

        //now make sure it gets deserialized correctly.
        JaxbExample readEx = mapper.readValue(json, JaxbExample.class);
        assertEquals(ex.qname, readEx.qname);
        assertEquals(ex.qname1, readEx.qname1);
        assertEquals(ex.attributeProperty, readEx.attributeProperty);
        assertEquals(ex.elementProperty, readEx.elementProperty);
        assertEquals(ex.wrappedElementProperty, readEx.wrappedElementProperty);
        assertEquals(ex.enumProperty, readEx.enumProperty);
        assertNull(readEx.propertyToIgnore);
    }

    public void testRootNameAccess() throws Exception
    {
        final TypeFactory tf = MAPPER.getTypeFactory();
        AnnotationIntrospector ai = new JaxbAnnotationIntrospector();
        // If no @XmlRootElement, should get null (unless pkg has etc)
        assertNull(ai.findRootName(AnnotatedClassResolver.resolve(MAPPER.serializationConfig(),
                tf.constructType(SimpleBean.class), null)));
        // With @XmlRootElement, but no name, empty String
        PropertyName rootName = ai.findRootName(AnnotatedClassResolver.resolve(MAPPER.serializationConfig(),
                tf.constructType(NamespaceBean.class), null));
        assertNotNull(rootName);
        assertEquals("", rootName.getSimpleName());
        assertEquals("urn:class", rootName.getNamespace());

        // and otherwise explicit name
        rootName = ai.findRootName(AnnotatedClassResolver.resolve(MAPPER.serializationConfig(),
                tf.constructType(RootNameBean.class), null));
        assertNotNull(rootName);
        assertEquals("test", rootName.getSimpleName());
        assertNull(rootName.getNamespace());
    }
    
    // JAXB can specify that properties are to be written in alphabetic order...
    public void testSerializationAlphaOrdering() throws Exception
    {
        assertEquals("{\"a\":1,\"b\":2,\"c\":3}", MAPPER.writeValueAsString(new AlphaBean()));
    }

    /**
     * Additional simple tests to ensure we will retain basic namespace information
     * now that it can be included
     */
    public void testNamespaces() throws Exception
    {
        final TypeFactory tf = MAPPER.getTypeFactory();
        JaxbAnnotationIntrospector ai = new JaxbAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolve(MAPPER.serializationConfig(),
                tf.constructType(NamespaceBean.class), null);
        AnnotatedField af = _findField(ac, "string");
        assertNotNull(af);
        PropertyName pn = ai.findNameForDeserialization(af);
        assertNotNull(pn);
        
        // JAXB seems to assert field name instead of giving "use default"...
        assertEquals("", pn.getSimpleName());
        assertEquals("urn:method", pn.getNamespace());
    }
 
    private AnnotatedField _findField(AnnotatedClass ac, String name)
    {
        for (AnnotatedField af : ac.fields()) {
            if (name.equals(af.getName())) {
                return af;
            }
        }
        return null;
    }
}
