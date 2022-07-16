package com.fasterxml.jackson.module.jakarta.xmlbind.introspect;

import jakarta.xml.bind.annotation.XmlElement;

import tools.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.jakarta.xmlbind.ModuleTestBase;

/**
 * Unit test(s) to verify that annotations from super classes and
 * interfaces are properly used (for example, wrt [JACKSON-450])
 */
public class TestAnnotationPriority extends ModuleTestBase
{
    /*
    /**********************************************************
    /* Helper beans
    /**********************************************************
     */

    public interface Identifiable {
        public String getId();
        public void setId(String i);
    }

    static abstract class IdBase
    {
        protected String id;

        protected IdBase(String id) { this.id = id; }
        
        @XmlElement(name="name")
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
    }

    static class IdBean extends IdBase implements Identifiable {
        public IdBean(String id) { super(id); }
    }
    
    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    public void testInterfacesAndClasses() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        String json = mapper.writeValueAsString(new IdBean("foo"));
        assertEquals("{\"name\":\"foo\"}", json);
    }
}
