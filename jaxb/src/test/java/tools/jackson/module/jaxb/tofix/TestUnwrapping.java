package tools.jackson.module.jaxb.tofix;

import javax.xml.bind.annotation.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import tools.jackson.databind.*;
import tools.jackson.databind.introspect.AnnotationIntrospectorPair;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;

import tools.jackson.module.jaxb.BaseJaxbTest;
import tools.jackson.module.jaxb.JaxbAnnotationIntrospector;
import tools.jackson.module.jaxb.testutil.failure.JacksonTestFailureExpected;

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
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(
                new JacksonAnnotationIntrospector(),
                new JaxbAnnotationIntrospector());
        ObjectMapper mapper = objectMapperBuilder()
                .annotationIntrospector(pair)
                .build();
            
//            mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector());
            // mapper.setAnnotationIntrospector(new JaxbAnnotationIntrospector());

        String json = mapper.writeValueAsString(bean);
        // !!! TODO: verify
        assertNotNull(json);
    } 
}
