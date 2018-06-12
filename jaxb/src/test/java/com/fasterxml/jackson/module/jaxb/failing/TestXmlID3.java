package com.fasterxml.jackson.module.jaxb.failing;

import java.util.Arrays;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

// for [modules-base#46]: XmlId semantics can not be supported by Jackson/JAXB-annotation-mapper
public class TestXmlID3 extends BaseJaxbTest
{
    static class HasID
    {
         String id;
         String name;

         @XmlID 
         @XmlElement
         public String getId() {
             return id;
         }
         
         @XmlElement
         public String getName() { return name; }
    }

    static class HasIDList
    {
        List<HasID> elements;
        HasID parent;

        // 29-May-2018, tatu: Following WOULD actually work, as long as @XmlId above
        //   was commented out
// @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property="id")
        
        @XmlElement
        public List<HasID> getElements() { return elements; }

        public void setElements(List<HasID> elements) { this.elements = elements; }

        @XmlElement
        public HasID getParent() { return parent; }
    }

    public void testIssue46() throws Exception
    {
        ObjectMapper mapper = getJaxbAndJacksonMapper();

        HasID hasID = new HasID();   
        hasID.id = "1"; hasID.name="name1";
        HasID hasID2 = new HasID();
        hasID2.id = "1"; hasID2.name="name1";
        
        HasIDList idList = new HasIDList();
        idList.setElements(Arrays.asList(hasID,hasID2));
        idList.parent = hasID2;

//System.err.println("->\n"+mapper.writeValueAsString(idList));

        assertEquals(aposToQuotes(
"{'elements':[{'id':'1','name':'name1'},{'id':'1','name':'name1'}],'parent':{'id':'1','name':'name1'}}"),
                mapper.writeValueAsString(idList));
    }
}
