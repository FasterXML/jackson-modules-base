package com.fasterxml.jackson.module.jaxb.types;

import java.util.*;

import javax.xml.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for handling of type-related JAXB annotations 
 */
public class TestJaxbPolymorphicMaps
    extends PolymorpicTestBase
{
    /*
    /**********************************************************
    /* Helper beans
    /**********************************************************
     */

    static class MapBean 
    {
        @XmlElements({
                @XmlElement(type=Buffalo.class, name="beefalot"),
                @XmlElement(type=Whale.class, name="whale")
        })
        public Map<Integer,Animal> animals;

        @XmlElementRefs({
                @XmlElementRef(type=Emu.class),
                @XmlElementRef(type=Cow.class)
        })
        public Map<Integer,Animal> otherAnimals;

        public MapBean() {
            animals = new HashMap<Integer,Animal>();
            otherAnimals = new HashMap<Integer,Animal>();
        }

        public void add(Integer key, Animal value) { animals.put(key, value); }
        public void addOther(Integer key, Animal value) { otherAnimals.put(key, value); }
    }

    /*
    /**********************************************************
    /* Tests
    /**********************************************************
     */

    public void testPolymorphicMap() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        Animal a = new Whale("Jaska", 3000);
        Animal b = new Whale("Arska", 2000);
        Animal c = new Whale("Pena", 1500);
        MapBean input = new MapBean();
        input.add(1, a);
        input.add(2, b);
        input.add(3, c);
        String str = mapper.writer().withDefaultPrettyPrinter().writeValueAsString(input);
        MapBean result = mapper.readValue(str, MapBean.class);
        Map<Integer,Animal> map = result.animals;
        assertEquals(3, map.size());
        assertEquals("Jaska", ((Whale) map.get(Integer.valueOf(1))).nickname);
        assertEquals("Arska", ((Whale) map.get(Integer.valueOf(2))).nickname);
        assertEquals("Pena", ((Whale) map.get(Integer.valueOf(3))).nickname);
    }

    public void testPolymorphicMapElementRefs() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        Animal a = new Cow("Jaska", 3000);
        Animal b = new Cow("Arska", 2000);
        Animal c = new Cow("Pena", 1500);
        MapBean input = new MapBean();
        input.addOther(1, a);
        input.addOther(2, b);
        input.addOther(3, c);
        String str = mapper.writeValueAsString(input);

        MapBean result = mapper.readValue(str, MapBean.class);
        Map<Integer,Animal> map = result.otherAnimals;
        assertEquals(3, map.size());
        assertEquals("Jaska", ((Cow) map.get(Integer.valueOf(1))).nickname);
        assertEquals("Arska", ((Cow) map.get(Integer.valueOf(2))).nickname);
        assertEquals("Pena", ((Cow) map.get(Integer.valueOf(3))).nickname);
    }
}
