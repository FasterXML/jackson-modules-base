package com.fasterxml.jackson.module.jaxb.failing;

import javax.xml.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

public class TestUnwrapping extends BaseJaxbTest
{
    @XmlRootElement
    static class Bean<R>
    {
        @JsonUnwrapped
        @XmlAnyElement(lax = true)
        @XmlElementRefs( { @XmlElementRef(name = "a", type = A.class),
            @XmlElementRef(name = "b", type = B.class) })
        public R r;
        public String name;

        public Bean() { }
    }

    static class A {
        public int count;

        public A() { }

        public A(int count) {
            this.count = count;
        }
    }

    static class B {
        public String type;

        public B() { }

        public B(String type) {
            this.type = type;
        }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */
    
    // not asserting anything
    public void testXmlElementAndXmlElementRefs() throws Exception
    {
        Bean<A> bean = new Bean<A>();
        bean.r = new A(12);
        bean.name = "test";
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(
                new JacksonAnnotationIntrospector(),
                new JaxbAnnotationIntrospector());
        ObjectMapper mapper = ObjectMapper.builder()
                .annotationIntrospector(pair)
                .build();
            
//            mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector());
            // mapper.setAnnotationIntrospector(new JaxbAnnotationIntrospector());

        String json = mapper.writeValueAsString(bean);
        // !!! TODO: verify
        assertNotNull(json);
    } 
}
