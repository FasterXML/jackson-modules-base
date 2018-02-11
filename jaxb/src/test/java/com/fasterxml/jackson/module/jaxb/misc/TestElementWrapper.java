package com.fasterxml.jackson.module.jaxb.misc;

import java.util.*;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

/**
 * Unit tests to verify handling of @XmlElementWrapper annotation.
 */
public class TestElementWrapper extends BaseJaxbTest
{
    /*
    /**********************************************************
    /* Helper beans
    /**********************************************************
     */

    // Beans for [JACKSON-436]
    static class Person {
        @XmlElementWrapper(name="phones")
        @XmlElement(type=Phone.class)
        public Collection<IPhone> phone;
    }

    interface IPhone {
        public String getNumber();
    }

    static class Phone implements IPhone
    {
        private String number;

        public Phone() { }
        
        public Phone(String number) { this.number = number; }
        @Override
        public String getNumber() { return number; }
        public void setNumber(String number) { this.number = number; }
    }

    // [Issue#13]
    static class Bean13
    {
        @XmlElementWrapper(name="wrap")
        public int id;
    }
    
    // [Issue#25]: should also work with 'default' name
    static class Bean25
    {
        @XmlElement(name="element")
// This would work
// @XmlElementWrapper(name="values")
        @XmlElementWrapper
        public List<Integer> values;

        public Bean25() { }
        public Bean25(int... v0) {
            values = new ArrayList<Integer>();
            for (int v : v0) {
                values.add(v);
            }
        }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    // [JACKSON-436]
    public void testWrapperWithCollection() throws Exception
    {
        ObjectMapper mapper = getJaxbMapperBuilder()
                // for fun, force renaming with wrapper annotation, even for JSON
                .enable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME)
                .build();
        Collection<IPhone> phones = new HashSet<IPhone>();
        phones.add(new Phone("555-6666"));
        Person p = new Person();
        p.phone = phones;

        String json = mapper.writeValueAsString(p);

        // as per 
        assertEquals("{\"phones\":[{\"number\":\"555-6666\"}]}", json);

//        System.out.println("JSON == "+json);

        Person result = mapper.readValue(json, Person.class);
        assertNotNull(result.phone);
        assertEquals(1, result.phone.size());
        assertEquals("555-6666", result.phone.iterator().next().getNumber());
    }

    public void testWrapperRenaming() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        // verify that by default feature is off:
        assertFalse(mapper.isEnabled(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME));
        Bean13 input = new Bean13();
        input.id = 3;
        assertEquals("{\"id\":3}", mapper.writeValueAsString(input));
        // but if we create new instance, configure
        mapper = getJaxbMapperBuilder()
                .enable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME)
                .build();
        assertEquals("{\"wrap\":3}", mapper.writeValueAsString(input));
    }

    public void testWrapperDefaultName() throws Exception
    {
        ObjectMapper mapper = getJaxbMapperBuilder()
                .enable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME)
                .build();
        Bean25 input = new Bean25(1, 2, 3);
        final String JSON = "{\"values\":[1,2,3]}";
        assertEquals(JSON, mapper.writeValueAsString(input));

        // plus needs to come back ok as well
        Bean25 result = mapper.readValue(JSON, Bean25.class);
        assertNotNull(result);
        assertNotNull(result.values);
        assertEquals(3, result.values.size());

        // and finally verify roundtrip as well
        assertEquals(JSON, mapper.writeValueAsString(result));
    }
}
