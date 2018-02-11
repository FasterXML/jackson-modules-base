package com.fasterxml.jackson.module.jaxb.introspect;

import java.util.*;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

/**
 * Unit test(s) written for [JACKSON-303]; we should be able to detect setter
 * even though it is not annotated, because there is matching annotated getter.
 */
public class TestAccessType
    extends BaseJaxbTest
{
    @XmlRootElement(name = "model")
    @XmlAccessorType(XmlAccessType.NONE)
    public static class SimpleNamed
    {
       protected String name;

       @XmlElement
       public String getName() {
          return name;
       }

       public void setName(String name) {
          this.name = name;
       }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "LoggedActivity")
    public static class Bean288
    {
        @XmlElement(required = true, type = String.class)
        @XmlJavaTypeAdapter(MyAdapter.class)
        @XmlSchemaType(name = "date")
        public Date date;
    }

    public static class MyAdapter
        extends XmlAdapter<String, Date>
    {
        @Override
        public String marshal(Date arg) throws Exception {
            return "String="+arg.getTime();
        }
        @Override
        public Date unmarshal(String arg0) throws Exception {
            return new Date(Long.parseLong(arg0));
        }
    }

    // [jaxb-annotations#40]: Need to recognize more marker annotations
    @XmlAccessorType(XmlAccessType.NONE)
    public class Bean40
    {
        @XmlElement
        public int getA() { return 1; }

        @XmlElementWrapper(name="b")
        public int getX() { return 2; }

        @XmlElementRef
        public int getC() { return 3; }
    }    

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

     public void testXmlElementTypeDeser() throws Exception
     {
         ObjectMapper mapper = getJaxbMapper();

         SimpleNamed originalModel = new SimpleNamed();
         originalModel.setName("Foobar");
         String json = mapper.writeValueAsString(originalModel);
         SimpleNamed result = null;
         try {
             result = mapper.readValue(json, SimpleNamed.class);
         } catch (Exception ie) {
             fail("Failed to deserialize '"+json+"': "+ie.getMessage());
         }
         if (!"Foobar".equals(result.name)) {
             fail("Failed, JSON == '"+json+"')");
         }
     }

     public void testForJackson288() throws Exception
     {
         final long TIMESTAMP = 12345678L;
         ObjectMapper mapper = getJaxbMapper();
         Bean288 bean = mapper.readValue("{\"date\":"+TIMESTAMP+"}", Bean288.class);
         assertNotNull(bean);
         Date d = bean.date;
         assertNotNull(d);
         assertEquals(TIMESTAMP, d.getTime());
     }

     public void testInclusionIssue40() throws Exception
     {
         ObjectMapper mapper = getJaxbMapperBuilder()
                 .enable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME)
                 .build();
         String json = mapper.writeValueAsString(new Bean40());
         @SuppressWarnings("unchecked")
         Map<String,Object> map = mapper.readValue(json, Map.class);
         Map<String,Object> exp = new LinkedHashMap<String,Object>();
         exp.put("a", Integer.valueOf(1));
         exp.put("b", Integer.valueOf(2));
         exp.put("c", Integer.valueOf(3));
         assertEquals(exp, map);
     }
}
