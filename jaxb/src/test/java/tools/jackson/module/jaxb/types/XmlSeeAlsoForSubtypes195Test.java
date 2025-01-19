package tools.jackson.module.jaxb.types;

import javax.xml.bind.annotation.XmlSeeAlso;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.module.jaxb.BaseJaxbTest;

import static org.junit.jupiter.api.Assertions.*;

public class XmlSeeAlsoForSubtypes195Test
    extends BaseJaxbTest
{
    static class Root195 {
        public Base195 element;

        protected Root195() { }
        public Root195(Base195 e) {
            element = e;
        }
    }

    @JsonTypeInfo(include = As.WRAPPER_ARRAY, use = Id.SIMPLE_NAME)
    @XmlSeeAlso({Sub195A.class, Sub195B.class})
    abstract static class Base195 { }

    static class Sub195A extends Base195 { }
    static class Sub195B extends Base195 { }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = getJaxbAndJacksonMapper();

    // [modules-base#195]
    @Test
    public void testXmlSeeAlso195() throws Exception
    {
        String json = MAPPER.writeValueAsString(new Root195(new Sub195B()));
        assertEquals("{\"element\":[\"Sub195B\",{}]}", json);
        Root195 result = MAPPER.readValue(json, Root195.class);
        assertEquals(Sub195B.class, result.element.getClass());
    }
}
