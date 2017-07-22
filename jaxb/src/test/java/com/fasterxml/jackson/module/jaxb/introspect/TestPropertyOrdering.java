package com.fasterxml.jackson.module.jaxb.introspect;

import javax.xml.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

public class TestPropertyOrdering
    extends BaseJaxbTest
{
    @XmlType(propOrder = {"cparty", "contacts"})
    @XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
    public class BeanFor268
    {
        private String cpartyDto = "dto";
        private int[] contacts = new int[] { 1, 2, 3 };
	
        @XmlElement(name="cparty")
        public String getCpartyDto() { return cpartyDto; }
        public void setCpartyDto(String cpartyDto) { this.cpartyDto = cpartyDto; }
	
        @XmlElement(name="contacts")
        public int[] getContact() { return contacts; }
        public void setContact(int[] contacts) { this.contacts = contacts; }
    }

    /* Also, considering that JAXB actually seems to expect original
     * names for property ordering, let's see that alternative
     * annotation also works
     * (see [JACKSON-268] for more details)
     */
    @XmlType(propOrder = {"cpartyDto", "contacts"})
    @XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
    public class BeanWithImplicitNames
    {
        private String cpartyDto = "dto";
        private int[] contacts = new int[] { 1, 2, 3 };
        
        @XmlElement(name="cparty")
        public String getCpartyDto() { return cpartyDto; }
        public void setCpartyDto(String cpartyDto) { this.cpartyDto = cpartyDto; }
        
        @XmlElement(name="contacts")
        public int[] getContact() { return contacts; }
        public void setContact(int[] contacts) { this.contacts = contacts; }
    }

    @XmlType(propOrder={"b", "a", "c"})
    public static class AlphaBean2
    {
        public int c = 3;
        public int a = 1;
        public int b = 2;
    }
    
    /*
    /**********************************************************
    /* Tests
    /**********************************************************
     */

    public void testSerializationExplicitOrdering() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        assertEquals("{\"b\":2,\"a\":1,\"c\":3}", serializeAsString(mapper, new AlphaBean2()));
    }
    
    // Trying to reproduce [JACKSON-268]
    public void testOrderingWithRename() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        assertEquals("{\"cparty\":\"dto\",\"contacts\":[1,2,3]}", mapper.writeValueAsString(new BeanFor268()));
    }

    public void testOrderingWithOriginalPropName() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        assertEquals("{\"cparty\":\"dto\",\"contacts\":[1,2,3]}",
                mapper.writeValueAsString(new BeanWithImplicitNames()));
    }
}
