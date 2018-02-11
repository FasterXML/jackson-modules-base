package com.fasterxml.jackson.module.jaxb.introspect;

import java.util.*;

import javax.xml.bind.annotation.*;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotatedClassResolver;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.type.TypeFactory;

import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

/**
 * Simple testing that <code>AnnotationIntrospector.Pair</code> works as
 * expected, when used with Jackson and JAXB-based introspector.
 *
 * @author Tatu Saloranta
 */
public class TestIntrospectorPair
    extends BaseJaxbTest
{
    final static AnnotationIntrospector _jacksonAI = new JacksonAnnotationIntrospector();
    final static AnnotationIntrospector _jaxbAI = new JaxbAnnotationIntrospector();
    
    /*
    /**********************************************************
    /* Helper beans
    /**********************************************************
     */

    /**
     * Simple test bean for verifying basic field detection and property
     * naming annotation handling
     */
    @XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
    static class NamedBean
    {
        @JsonProperty
        private String jackson = "1";

        @XmlElement(name="jaxb")
        protected String jaxb = "2";

        @JsonProperty("bothJackson")
        @XmlElement(name="bothJaxb")
        private String bothString = "3";

        public String notAGetter() { return "xyz"; }
    }

    /**
     * Another bean for verifying details of property naming
     */
    @XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
    static class NamedBean2
    {
        @JsonProperty("")
        @XmlElement(name="jaxb")
        public String foo = "abc";

        @JsonProperty("jackson")
        @XmlElement()
        public String getBar() { return "123"; }

        // JAXB, alas, requires setters for all properties too
        public void setBar(String v) { }
    }

    /**
     * And a bean to check how "ignore" annotations work with
     * various combinations of annotation introspectors
     */
    @XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
    static class IgnoreBean
    {
        @JsonIgnore
        public int getNumber() { return 13; }

        @XmlTransient
        public String getText() { return "abc"; }

        public boolean getAny() { return true; }
    }

    @XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
    static class IgnoreFieldBean
    {
        @JsonIgnore public int number = 7;
        @XmlTransient public String text = "123";
        public boolean any = true;
    }

    @XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
    @XmlRootElement(name="test", namespace="urn:whatever")
    static class NamespaceBean
    {
        public String string;
    }

    // Testing [JACKSON-495]
    static class CreatorBean {
        @JsonCreator
        public CreatorBean(@JsonProperty("name") String name) {
            ;
        }
    }
    
    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    public void testSimple() throws Exception
    {
        ObjectMapper mapper;
        AnnotationIntrospector pair;
        Map<String,Object> result;

        // first: test with Jackson/Jaxb pair (jackson having precedence)
        pair = new AnnotationIntrospectorPair(_jacksonAI, _jaxbAI);
        mapper = ObjectMapper.builder()
                .annotationIntrospector(pair)
                .build();

        result = writeAndMap(mapper, new NamedBean());
        assertEquals(3, result.size());
        assertEquals("1", result.get("jackson"));
        assertEquals("2", result.get("jaxb"));
        // jackson one should have priority
        assertEquals("3", result.get("bothJackson"));

        pair = new AnnotationIntrospectorPair(_jaxbAI, _jacksonAI);
        mapper = ObjectMapper.builder()
                .annotationIntrospector(pair)
                .build();

        result = writeAndMap(mapper, new NamedBean());
        assertEquals(3, result.size());
        assertEquals("1", result.get("jackson"));
        assertEquals("2", result.get("jaxb"));
        // JAXB one should have priority
        assertEquals("3", result.get("bothJaxb"));
    }

    public void testNaming() throws Exception
    {
        ObjectMapper mapper;
        AnnotationIntrospector pair;
        Map<String,Object> result;

        // first: test with Jackson/Jaxb pair (jackson having precedence)
        pair = new AnnotationIntrospectorPair(_jacksonAI, _jaxbAI);
        mapper = ObjectMapper.builder()
            .annotationIntrospector(pair)
            .build();

        result = writeAndMap(mapper, new NamedBean2());
        assertEquals(2, result.size());
        // order shouldn't really matter here...
        assertEquals("123", result.get("jackson"));
        assertEquals("abc", result.get("jaxb"));

        pair = new AnnotationIntrospectorPair(_jaxbAI, _jacksonAI);
        mapper = ObjectMapper.builder()
                .annotationIntrospector(pair)
                .build();

        result = writeAndMap(mapper, new NamedBean2());
        /* Hmmh. Not 100% sure what JAXB would dictate.... thus...
         */
        assertEquals(2, result.size());
        assertEquals("abc", result.get("jaxb"));
        //assertEquals("123", result.get("jackson"));
    }

    public void testSimpleIgnore() throws Exception
    {
        // first: only Jackson introspector (default)
        ObjectMapper mapper = new ObjectMapper();
        Map<String,Object> result = writeAndMap(mapper, new IgnoreBean());
        assertEquals(2, result.size());
        assertEquals("abc", result.get("text"));
        assertEquals(Boolean.TRUE, result.get("any"));

        // Then JAXB only
        mapper = ObjectMapper.builder()
                .annotationIntrospector(_jaxbAI)
                .build();

        // jackson one should have priority
        result = writeAndMap(mapper, new IgnoreBean());
        assertEquals(2, result.size());
        assertEquals(Integer.valueOf(13), result.get("number"));
        assertEquals(Boolean.TRUE, result.get("any"));

        // then both, Jackson first
        mapper = ObjectMapper.builder()
                .annotationIntrospector(new AnnotationIntrospectorPair(_jacksonAI, _jaxbAI))
                .build();

        result = writeAndMap(mapper, new IgnoreBean());
        assertEquals(1, result.size());
        assertEquals(Boolean.TRUE, result.get("any"));

        // then both, JAXB first
        mapper = ObjectMapper.builder()
                .annotationIntrospector(new AnnotationIntrospectorPair(_jaxbAI, _jacksonAI))
                .build();

        result = writeAndMap(mapper, new IgnoreBean());
        assertEquals(1, result.size());
        assertEquals(Boolean.TRUE, result.get("any"));
    }

    public void testSimpleFieldIgnore() throws Exception
    {
        ObjectMapper mapper;

        // first: only Jackson introspector (default)
        mapper = new ObjectMapper();
        Map<String,Object> result = writeAndMap(mapper, new IgnoreFieldBean());
        assertEquals(2, result.size());
        assertEquals("123", result.get("text"));
        assertEquals(Boolean.TRUE, result.get("any"));

        // Then JAXB only
        mapper = ObjectMapper.builder()
                .annotationIntrospector(_jaxbAI)
                .build();

        // jackson one should have priority
        result = writeAndMap(mapper, new IgnoreFieldBean());
        assertEquals(2, result.size());
        assertEquals(Integer.valueOf(7), result.get("number"));
        assertEquals(Boolean.TRUE, result.get("any"));

        // then both, Jackson first
        mapper = ObjectMapper.builder()
                .annotationIntrospector(new AnnotationIntrospectorPair(_jacksonAI, _jaxbAI))
                .build();

        result = writeAndMap(mapper, new IgnoreFieldBean());
        assertEquals(1, result.size());
        assertEquals(Boolean.TRUE, result.get("any"));

        // then both, JAXB first
        mapper = ObjectMapper.builder()
                .annotationIntrospector(new AnnotationIntrospectorPair(_jaxbAI, _jacksonAI))
                .build();

        result = writeAndMap(mapper, new IgnoreFieldBean());
        assertEquals(1, result.size());
        assertEquals(Boolean.TRUE, result.get("any"));
    }

    public void testRootName() throws Exception
    {
        // first: test with Jackson/Jaxb pair (jackson having precedence)
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(_jacksonAI, _jaxbAI);
        ObjectMapper mapper = ObjectMapper.builder()
            .annotationIntrospector(pair)
            .build();
        TypeFactory tf = mapper.getTypeFactory();

        assertNull(pair.findRootName(AnnotatedClassResolver.resolve(mapper.serializationConfig(),
                tf.constructType(NamedBean.class), null)));
        PropertyName name = pair.findRootName(AnnotatedClassResolver.resolve(mapper.serializationConfig(),
                tf.constructType(NamespaceBean.class), null));
        assertNotNull(name);
        assertEquals("test", name.getSimpleName());
        assertEquals("urn:whatever", name.getNamespace());

        // then reverse; should make no difference
        pair = new AnnotationIntrospectorPair(_jaxbAI, _jacksonAI);
        name = pair.findRootName(AnnotatedClassResolver.resolve(mapper.serializationConfig(),
                tf.constructType(NamedBean.class), null));
        assertNull(name);
        
        name = pair.findRootName(AnnotatedClassResolver.resolve(mapper.serializationConfig(),
                tf.constructType(NamespaceBean.class), null));
        assertEquals("test", name.getSimpleName());
        assertEquals("urn:whatever", name.getNamespace());
    }

    /**
     * Test that will just use Jackson annotations, but did trigger [JACKSON-495] due to a bug
     * in JAXB annotation introspector.
     */
    public void testIssue495() throws Exception
    {
        ObjectMapper mapper = ObjectMapper.builder()
                .annotationIntrospector(new AnnotationIntrospectorPair(_jacksonAI, _jaxbAI))
                .build();
        CreatorBean bean = mapper.readValue("{\"name\":\"foo\"}", CreatorBean.class);
        assertNotNull(bean);
    }
}
