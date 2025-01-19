package com.fasterxml.jackson.module.jaxb.tofix;

import javax.xml.bind.annotation.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.testutil.failure.JacksonTestFailureExpected;

import static org.junit.jupiter.api.Assertions.*;

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
    @JacksonTestFailureExpected
    @Test
    public void testXmlElementAndXmlElementRefs() throws Exception
    {
        Bean<A> bean = new Bean<A>();
        bean.r = new A(12);
        bean.name = "test";
        ObjectMapper mapper = new ObjectMapper();
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(
                new JacksonAnnotationIntrospector(),
                new JaxbAnnotationIntrospector(mapper.getTypeFactory())
        );
        mapper.setAnnotationIntrospector(pair);
            
//            mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector());
            // mapper.setAnnotationIntrospector(new JaxbAnnotationIntrospector());

        String json = mapper.writeValueAsString(bean);
        // !!! TODO: verify
        assertNotNull(json);
    } 
}
