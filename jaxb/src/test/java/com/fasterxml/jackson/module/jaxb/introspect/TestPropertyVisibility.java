package com.fasterxml.jackson.module.jaxb.introspect;

import java.io.IOException;

import javax.xml.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.*;

import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

public class TestPropertyVisibility
    extends BaseJaxbTest
{
    @XmlAccessorType(XmlAccessType.NONE)
    protected static class Bean354
    {
        protected String name = "foo";
    
        @XmlElement
        protected String getName() { return name; }

        public void setName(String s) { name = s; }
    }

    // Note: full example would be "Content"; but let's use simpler demonstration here, easier to debug
    @XmlAccessorType(XmlAccessType.PROPERTY)
    static class Jackson539Bean
    {
        protected int type;
        
        @XmlTransient
        public String getType() {
            throw new UnsupportedOperationException();
        }

        public void setType(String type) {
            throw new UnsupportedOperationException();
        }

        @XmlAttribute(name = "type")
        public int getRawType() {
           return type;
        }

        public void setRawType(int type) {
           this.type = type;
        }
    }

    // for [modules-base#44]
    static class Foo44 {
        public String foo = "bar";
    }

    @XmlAccessorType(XmlAccessType.NONE)
    @JsonPropertyOrder({ "object", "other" })
    static class NoneAccessBean
    {
        @XmlElements({
                @XmlElement(type=Foo44.class, name="foo")
        })
        public Object object;

        @XmlElement
        public Object other;

        public NoneAccessBean() { }
        public NoneAccessBean(Object o) { object = o; }
        public NoneAccessBean(Object o, Object b) {
            object = o;
            other = b;
        }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    private final ObjectMapper MAPPER = getJaxbMapper();
    
    // Verify serialization wrt [JACKSON-354]
    //
    // NOTE: fails currently because we use Bean Introspector which only sees public methods -- need to rewrite
    public void testJackson354Serialization() throws IOException
    {
        assertEquals("{\"name\":\"foo\"}", MAPPER.writeValueAsString(new Bean354()));
    }

    // For [JACKSON-539]
    public void testJacksonSerialization()
            throws Exception
    {
        /* Earlier
        Content content = new Content();
        content.setRawType("application/json");
        String json = mapper.writeValueAsString(content);
        Content content2 = mapper.readValue(json, Content.class); // deserialize
        assertNotNull(content2);
         */
        
        Jackson539Bean input = new Jackson539Bean();
        input.type = 123;
        String json = MAPPER.writeValueAsString(input);
        Jackson539Bean result = MAPPER.readValue(json, Jackson539Bean.class);
        assertNotNull(result);
        assertEquals(123, result.type);
    }

    // for [modules-base#44]
    public void testNoneAccessWithXmlElements() throws Exception
    {
        NoneAccessBean input = new NoneAccessBean(new Foo44());
        assertEquals(aposToQuotes("{'object':{'foo':{'foo':'bar'}},'other':null}"),
                MAPPER.writeValueAsString(input));
    }
}
