package com.fasterxml.jackson.module.jakarta.xmlbind.types;

import java.util.*;

import jakarta.xml.bind.annotation.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for handling of type-related JAXB annotations with collection (List)
 * types.
 */
public class TestJaxbPolymorphicLists
    extends PolymorpicTestBase
{
    /*
    /**********************************************************
    /* Helper beans
    /**********************************************************
     */

     static class ListBean 
     {
         @XmlElements({
                 @XmlElement(type=Buffalo.class, name="beefalot"),
                 @XmlElement(type=Whale.class, name="whale")
         })
         public List<Animal> animals;

         @XmlElementRefs({
             @XmlElementRef(type=Emu.class),
             @XmlElementRef(type=Cow.class)
         })
		 public List<Animal> otherAnimals;

         public ListBean() { }
         public ListBean(Animal... a) {
             animals = new ArrayList<Animal>();
             for (Animal an : a) {
                 animals.add(an);
             }
         }
     }

     static class Leopard extends Animal {
    	 public Leopard() { super("Lez"); }
     }

     static class ShortListHolder {
         @XmlElement(name="id", type=Short.class)
         public List<Short> ids;
     }
     
    /*
    /**********************************************************
    /* Tests
    /**********************************************************
     */
     
     /**
      * And then a test for collection types
      */
     @Test
    public void testPolymorphicList() throws Exception
     {
         ObjectMapper mapper = getJaxbMapper();
         ListBean input = new ListBean(new Whale("bluey", 150),
                 new Buffalo("Bob", "black")
         );
         String str = mapper.writeValueAsString(input);
         // Let's assume it's ok, and try deserialize right away:         
         ListBean result = mapper.readValue(str, ListBean.class);
         assertEquals(2, result.animals.size());
         Animal a1 = result.animals.get(0);
         assertNotNull(a1);
         assertEquals(Whale.class, a1.getClass());
         assertEquals("bluey", a1.nickname);
         assertEquals(150, ((Whale)a1).weightInTons); 
         Animal a2 = result.animals.get(1);
         assertNotNull(a2);
         assertEquals(Buffalo.class, a2.getClass());
         assertEquals("Bob", a2.nickname);
         assertEquals("black", ((Buffalo) a2).hairColor);
     }

     /**
      * And then a test for collection types using element ref(s)
      */
     @Test
    public void testPolymorphicListElementRef() throws Exception
     {
         ObjectMapper mapper = getJaxbMapper();
         ListBean input = new ListBean();
         input.otherAnimals = Arrays.asList(
                 new Cow("bluey", 150),
                 new Emu("Bob", "black")
         );
         String str = mapper.writeValueAsString(input);
         // Let's assume it's ok, and try deserialize right away:
         ListBean result = mapper.readValue(str, ListBean.class);
         assertEquals(2, result.otherAnimals.size());
         Animal a1 = result.otherAnimals.get(0);
         assertNotNull(a1);
         assertEquals(Cow.class, a1.getClass());
         assertEquals("bluey", a1.nickname);
         assertEquals(150, ((Cow)a1).weightInPounds);
         Animal a2 = result.otherAnimals.get(1);
         assertNotNull(a2);
         assertEquals(Emu.class, a2.getClass());
         assertEquals("Bob", a2.nickname);
         assertEquals("black", ((Emu) a2).featherColor);
     }

     // [JACKSON-348]
     @Test
    public void testShortList() throws Exception
     {
         ShortListHolder holder = getJaxbMapper().readValue("{\"id\":[1,2,3]}",
                 ShortListHolder.class);
         assertNotNull(holder.ids);
         assertEquals(3, holder.ids.size());
         assertSame(Short.valueOf((short)1), holder.ids.get(0));
         assertSame(Short.valueOf((short)2), holder.ids.get(1));
         assertSame(Short.valueOf((short)3), holder.ids.get(2));
     }
}
