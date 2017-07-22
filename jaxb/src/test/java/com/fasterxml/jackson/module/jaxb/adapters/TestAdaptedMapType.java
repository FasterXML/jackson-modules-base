package com.fasterxml.jackson.module.jaxb.adapters;

import java.io.*;
import java.util.*;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

/**
 * Tests for verifying JAXB adapter handling for {@link java.util.Map}
 * types.
 */
public class TestAdaptedMapType extends BaseJaxbTest
{
    static class ObjectContainingAMap
    {
        private Map<String, String> myMap;

        @XmlJavaTypeAdapter(MapAdapter.class)
        public Map<String, String> getMyMap() {
            return myMap;
        }

        public void setMyMap(Map<String, String> myMap) {
          this.myMap = myMap;
        }
    }

    static class StringMapWrapper {
        @XmlJavaTypeAdapter(StringMapAdapter.class)
        public Map<String,String> values = new LinkedHashMap<String,String>();
    }
    
    static class StringMapAdapter extends XmlAdapter<Map<String,String>, Map<String,String>>
    {
        @Override
        public Map<String, String> marshal(Map<String, String> input)
        {
            LinkedHashMap<String,String> result = new LinkedHashMap<String,String>();
            for (Map.Entry<String,String> entry : input.entrySet()) {
                result.put(entry.getKey(), "M-"+entry.getValue());
            }
            return result;
        }

        @Override
        public Map<String, String> unmarshal(Map<String, String> input)
        {
            LinkedHashMap<String,String> result = new LinkedHashMap<String,String>();
            for (Map.Entry<String,String> entry : input.entrySet()) {
                result.put(entry.getKey(), "U-"+entry.getValue());
            }
            return result;
        }
    }
    
    /*
    /**********************************************************
    /* Tests
    /**********************************************************
     */
    
    public void testJacksonAdaptedMapType() throws IOException
    {
        ObjectContainingAMap obj = new ObjectContainingAMap();
        obj.setMyMap(new LinkedHashMap<String, String>());
        obj.getMyMap().put("this", "that");
        obj.getMyMap().put("how", "here");

        ObjectMapper mapper = getJaxbMapper();

        // Ok, first serialize:
        String json = mapper.writeValueAsString(obj);
        
        obj = mapper.readValue(json, ObjectContainingAMap.class);
        Map<String,String> map = obj.getMyMap();
        assertNotNull(map);
        assertEquals(2, map.size());
        assertEquals("here", map.get("how"));
    }

    public void testStringMaps() throws IOException
    {
        ObjectMapper mapper = getJaxbMapper();
        StringMapWrapper map = mapper.readValue("{\"values\":{\"a\":\"b\"}}", StringMapWrapper.class);
        assertNotNull(map.values);
        assertEquals(1, map.values.size());
        assertEquals("U-b", map.values.get("a"));

        // and then out again
        String json = mapper.writeValueAsString(map);
        assertEquals("{\"values\":{\"a\":\"M-U-b\"}}", json);
    }
}
