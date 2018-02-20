package com.fasterxml.jackson.module.afterburner.deser;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class TestSingleArgCtors extends AfterburnerTestBase
{
    static class Node {
        public String name;

        public int value;

        public Node() { }

        @JsonCreator
        public Node(String n) {
            name = n;
            value = -1;
        }
    }

    static class Node2 {
        public String name;

        public int value1;
        public int value2;

        public Node2() { }

        @JsonCreator
        public Node2(String n) {
            name = n;
            value1 = -1;
            value2 = -2;
        }
    }

    static class Node3 {
        public String name;

        public int value1;
        public int value2;
        public int value3;

        public Node3() { }

        @JsonCreator
        public Node3(String n) {
            name = n;
            value1 = -1;
            value2 = -2;
            value3 = -3;
        }
    }

    static class Node4 {
        public String name;

        public int value1;
        public int value2;
        public int value3;
        public int value4;

        public Node4() { }

        @JsonCreator
        public Node4(String n) {
            name = n;
            value1 = -1;
            value2 = -2;
            value3 = -3;
            value4 = -4;
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = newAfterburnerMapper();

    public void testSingleStringArgCtor() throws Exception
    {
        Node bean = MAPPER.readValue(quote("Foobar"), Node.class);
        assertNotNull(bean);
        assertEquals(-1, bean.value);
        assertEquals("Foobar", bean.name);
    }

    public void testSingleStringArgCtor2() throws Exception
    {
        Node2 bean = MAPPER.readValue(quote("Foobar"), Node2.class);
        assertNotNull(bean);
        assertEquals(-1, bean.value1);
        assertEquals(-2, bean.value2);
        assertEquals("Foobar", bean.name);
    }

    public void testSingleStringArgCtor3() throws Exception
    {
        Node3 bean = MAPPER.readValue(quote("Foobar"), Node3.class);
        assertNotNull(bean);
        assertEquals(-1, bean.value1);
        assertEquals(-2, bean.value2);
        assertEquals(-3, bean.value3);
        assertEquals("Foobar", bean.name);
    }

    public void testSingleStringArgCtor4() throws Exception
    {
        Node4 bean = MAPPER.readValue(quote("Foobar"), Node4.class);
        assertNotNull(bean);
        assertEquals(-1, bean.value1);
        assertEquals(-2, bean.value2);
        assertEquals(-3, bean.value3);
        assertEquals(-4, bean.value4);
        assertEquals("Foobar", bean.name);
    }
}
