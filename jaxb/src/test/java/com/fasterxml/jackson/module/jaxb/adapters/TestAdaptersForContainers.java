package com.fasterxml.jackson.module.jaxb.adapters;

import java.util.*;
import java.util.Map.Entry;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;
import com.fasterxml.jackson.module.jaxb.introspect.TestJaxbAnnotationIntrospector.KeyValuePair;

/**
 * Unit tests to check that {@link XmlAdapter}s also work with
 * container types (Lists, Maps)
 */
public class TestAdaptersForContainers extends BaseJaxbTest
{
    // Support for Maps

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ParentJAXBBean
    {
        @XmlJavaTypeAdapter(JAXBMapAdapter.class) 
        private Map<String, String> params = new HashMap<String, String>();

        public Map<String, String> getParams() {
            return params;
        }

        public void setParams(Map<String, String> params) {
            this.params = params;
        }
    }

    public static class JAXBMapAdapter extends XmlAdapter<List<KeyValuePair>,Map<String, String>> { 

        @Override
        public List<KeyValuePair> marshal(Map<String, String> arg0) throws Exception { 
                List<KeyValuePair> keyValueList = new ArrayList<KeyValuePair>();
                for(Entry<String, String> entry : arg0.entrySet()) { 
                        KeyValuePair keyValuePair = new KeyValuePair();
                        keyValuePair.setKey(entry.getKey());
                        keyValuePair.setValue(entry.getValue());
                        keyValueList.add(keyValuePair);
                        } 
                return keyValueList; 
        } 
        @Override
        public Map<String, String> unmarshal(List<KeyValuePair> arg0) throws Exception 
        { 
            HashMap<String, String> hashMap = new HashMap<String, String>(); 
            for (int i = 0; i < arg0.size(); i++) {
                hashMap.put(arg0.get(i).getKey(), arg0.get(i).getValue());
            }
            return hashMap; 
        } 
    }
    
    // [JACKSON-722]
    
    public static class SillyAdapter extends XmlAdapter<String, Date>
    {
        public SillyAdapter() { }

        @Override
        public Date unmarshal(String date) throws Exception {
            return new Date(29L);
        }

        @Override
        public String marshal(Date date) throws Exception {
            return "XXX";
        }
    }

    static class Wrapper {
        @XmlJavaTypeAdapter(SillyAdapter.class)
        public List<Date> values;

        public Wrapper() { }
        public Wrapper(long l) {
            values = new ArrayList<Date>();
            values.add(new Date(l));
        }
    }

    static class WrapperWithGetterAndSetter {
        private List<Date> values;

        public WrapperWithGetterAndSetter() { }
        public WrapperWithGetterAndSetter(long l) {
            values = new ArrayList<Date>();
            values.add(new Date(l));
        }

        @XmlJavaTypeAdapter(SillyAdapter.class)
        public List<Date> getValues() {
            return values;
        }

        public void setValues(List<Date> values) {
            this.values = values;
        }
    }
    
    /*
    /**********************************************************
    /* Unit tests, Lists
    /**********************************************************
     */
 
    public void testAdapterForList() throws Exception
    {
        Wrapper w = new Wrapper(123L);
        assertEquals("{\"values\":[\"XXX\"]}", getJaxbMapper().writeValueAsString(w));
    }

    public void testSimpleAdapterDeserialization() throws Exception
    {
        Wrapper w = getJaxbMapper().readValue("{\"values\":[\"abc\"]}", Wrapper.class);
        assertNotNull(w);
        assertNotNull(w.values);
        assertEquals(1, w.values.size());
        assertEquals(29L, w.values.get(0).getTime());
    }

    public void testAdapterOnGetterSerialization() throws Exception
    {
        WrapperWithGetterAndSetter w = new WrapperWithGetterAndSetter(123L);
        assertEquals("{\"values\":[\"XXX\"]}", getJaxbMapper().writeValueAsString(w));
    }

    public void testAdapterOnGetterDeserialization() throws Exception
    {
        WrapperWithGetterAndSetter w = getJaxbMapper().readValue("{\"values\":[\"abc\"]}",
                WrapperWithGetterAndSetter.class);
        assertNotNull(w);
        assertNotNull(w.values);
        assertEquals(1, w.values.size());
        assertEquals(29L, w.values.get(0).getTime());
    }

    /*
    /**********************************************************
    /* Unit tests, Map-related
    /**********************************************************
     */
    
    public void testAdapterForBeanWithMap() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        ParentJAXBBean parentJaxbBean = new ParentJAXBBean();
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("sampleKey", "sampleValue");
        parentJaxbBean.setParams(params);
        
        String json = mapper.writeValueAsString(parentJaxbBean);

        // uncomment to see what the json looks like.
        //System.out.println(json);
         
         //now make sure it gets deserialized correctly.
         ParentJAXBBean readEx = mapper.readValue(json, ParentJAXBBean.class);
         assertEquals("sampleValue", readEx.getParams().get("sampleKey"));
    }
    
}
