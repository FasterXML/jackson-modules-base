package com.fasterxml.jackson.module.jaxb.types;

import java.util.*;

import javax.xml.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for handling of type-related JAXB annotations 
 * 
 * @author Tatu Saloranta
 * @author Ryan Heaton
 */
public class TestJaxbPolymorphic 
    extends PolymorpicTestBase
{
    /*
    /**********************************************************
    /* Helper beans
    /**********************************************************
     */

     static class Bean 
     {
         @XmlElements({
                 @XmlElement(type=Buffalo.class, name="beefalot"),
                 @XmlElement(type=Whale.class, name="whale")
         })
         public Animal animal;

         @XmlElementRefs({
                 @XmlElementRef(type=Emu.class),
                 @XmlElementRef(type=Cow.class)
         })
         public Animal other;

         public Bean() { }
         public Bean(Animal a) { animal = a; }
     }

     static class ArrayBean 
     {
         @XmlElements({
                 @XmlElement(type=Buffalo.class, name="b"),
                 @XmlElement(type=Whale.class, name="w")
         })
         public Animal[] animals;

         @XmlElementRefs({
                 @XmlElementRef(type=Emu.class),
                 @XmlElementRef(type=Cow.class)
         })
         public Animal[] otherAnimals;

         public ArrayBean() { }
         public ArrayBean(Animal... a) {
             animals = a;
         }
     }

     @XmlSeeAlso({ BaseImpl.class })
     abstract static class Base {
     }

     static class BaseImpl extends Base
     {
         public String name;

         public BaseImpl() { }
         public BaseImpl(String n) { name = n; }
     }

    static class ContainerForBase
    {
        @XmlElements({ })
//            @XmlElement(type=BaseImpl.class, name="baseImpl"),
        public Base[] stuff;
    }
     
    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = getJaxbMapper();
    
    //First a simple test with non-collection field

    @SuppressWarnings("unchecked")
    public void testSinglePolymorphic() throws Exception
    {
         Bean input = new Bean(new Buffalo("Billy", "brown"));
         String str = MAPPER.writeValueAsString(input);
         // First: let's verify output looks like what we expect:
         Map<String,Object> map = MAPPER.readValue(str, Map.class);
         assertEquals(2, map.size());
         Map<String,Object> map2 = (Map<String,Object>) map.get("animal");
         assertNotNull(map2);
         // second level, should have type info as WRAPPER_OBJECT
         assertEquals(1, map2.size());
         assertTrue(map2.containsKey("beefalot"));
         Map<String,Object> map3 = (Map<String,Object>) map2.get("beefalot");
         assertEquals(2, map3.size());
         // good enough, let's deserialize
         
         Bean result = MAPPER.readValue(str, Bean.class);
         Animal a = result.animal;
         assertNotNull(a);
         assertEquals(Buffalo.class, a.getClass());
         assertEquals("Billy", a.nickname);
         assertEquals("brown", ((Buffalo) a).hairColor);
     }

     public void testPolymorphicArray() throws Exception
     {
         Animal a1 = new Buffalo("Bill", "grey");
         Animal a2 = new Whale("moe", 3000);
         ArrayBean input = new ArrayBean(a1, null, a2);
         String str = MAPPER.writeValueAsString(input);
         ArrayBean result = MAPPER.readValue(str, ArrayBean.class);
         assertEquals(3, result.animals.length);
         a1 = result.animals[0];
         assertNull(result.animals[1]);
         a2 = result.animals[2];
         assertNotNull(a1);
         assertNotNull(a2);
         assertEquals(Buffalo.class, a1.getClass());
         assertEquals(Whale.class, a2.getClass());
         assertEquals("Bill", a1.nickname);
         assertEquals("grey", ((Buffalo) a1).hairColor);

         assertEquals("moe", a2.nickname);
         assertEquals(3000, ((Whale)a2).weightInTons); 
     }

     public void testPolymorphicArrayElementRef() throws Exception
     {
         Animal a1 = new Emu("Bill", "grey");
         Animal a2 = new Cow("moe", 3000);
         ArrayBean input = new ArrayBean();
         input.otherAnimals = new Animal[]{a1, null, a2};
         String str = MAPPER.writeValueAsString(input);
         ArrayBean result = MAPPER.readValue(str, ArrayBean.class);
         assertEquals(3, result.otherAnimals.length);
         a1 = result.otherAnimals[0];
         assertNull(result.otherAnimals[1]);
         a2 = result.otherAnimals[2];
         assertNotNull(a1);
         assertNotNull(a2);
         assertEquals(Emu.class, a1.getClass());
         assertEquals(Cow.class, a2.getClass());
         assertEquals("Bill", a1.nickname);
         assertEquals("grey", ((Emu) a1).featherColor);

         assertEquals("moe", a2.nickname);
         assertEquals(3000, ((Cow)a2).weightInPounds);
     }

     // For [Issue#1]
     public void testXmlSeeAlso() throws Exception
     {
         ContainerForBase input = new ContainerForBase();
         input.stuff = new Base[] { new BaseImpl("xyz") };
         String json = MAPPER.writeValueAsString(input);
         
         // so far so good. But can we read it back?
         ContainerForBase output = MAPPER.readValue(json, ContainerForBase.class);
         assertNotNull(output);
         assertNotNull(output.stuff);
         assertEquals(1, output.stuff.length);
         assertEquals(BaseImpl.class, output.stuff[0].getClass());
         assertEquals("xyz", ((BaseImpl) output.stuff[0]).name);
     }
}
