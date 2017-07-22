package com.fasterxml.jackson.module.jaxb.adapters;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

/**
 * Failing unit tests related to Adapter handling.
 */
public class TestIdentityAdapters extends BaseJaxbTest
{
    // [Issue-10]: Infinite recursion in "self" adapters
    public static class IdentityAdapter extends XmlAdapter<IdentityAdapterBean, IdentityAdapterBean> {
        @Override
        public IdentityAdapterBean unmarshal(IdentityAdapterBean b) {
            return new IdentityAdapterBean(b.value + "U");
        }

        @Override
        public IdentityAdapterBean marshal(IdentityAdapterBean b) {
            return new IdentityAdapterBean(b.value + "M");
        }   
    }

    public static class IdentityStringAdapter extends XmlAdapter<String, String> {
        @Override
        public String unmarshal(String b) {
            return b + "U";
        }
        @Override
        public String marshal(String b) {
            return b + "M";
        }   
    }
    
    @XmlJavaTypeAdapter(IdentityAdapter.class)
    static class IdentityAdapterBean
    {
        public String value;

        protected IdentityAdapterBean() { }
        public IdentityAdapterBean(String s) { value = s; }
    }

    static class IdentityAdapterPropertyBean
    {
        @XmlJavaTypeAdapter(IdentityStringAdapter.class)
        public String value;

        public IdentityAdapterPropertyBean() { }
        public IdentityAdapterPropertyBean(String s) { value = s; }
    }
    
    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */
    
    // [Issue-10]
    public void testIdentityAdapterForClass() throws Exception
    {
        IdentityAdapterBean input = new IdentityAdapterBean("A");
        ObjectMapper mapper = getJaxbMapper();
        
        String json = mapper.writeValueAsString(input);
        assertEquals("{\"value\":\"AM\"}", json);
        
        IdentityAdapterBean result = mapper.readValue(json, IdentityAdapterBean.class);
        assertEquals("AMU", result.value);
    }

    // [Issue-10]
    public void testIdentityAdapterForProperty() throws Exception
    {
        IdentityAdapterPropertyBean input = new IdentityAdapterPropertyBean("B");
        ObjectMapper mapper = getJaxbMapper();
        String json = mapper.writeValueAsString(input);
        assertEquals("{\"value\":\"BM\"}", json);
        IdentityAdapterPropertyBean result = mapper.readValue(json, IdentityAdapterPropertyBean.class);
        assertEquals("BMU", result.value);
    }
}
