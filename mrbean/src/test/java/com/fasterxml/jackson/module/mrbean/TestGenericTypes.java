package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class TestGenericTypes
    extends BaseTest
{
    /*
    /**********************************************************
    /* Test classes, enums
    /**********************************************************
     */

    public interface ListBean {
        public List<LeafBean> getLeaves();
    }

    public static class LeafBean {
        public String value;
    }
    
    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    /**
     * Test simple leaf-level bean with 2 implied _beanProperties
     */
    public void testSimpleInterface() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper().registerModule(new MrBeanModule());
        ListBean bean = mapper.readValue("{\"leaves\":[{\"value\":\"foo\"}] }", ListBean.class);
        assertNotNull(bean);
        List<LeafBean> leaves = bean.getLeaves();
        assertNotNull(leaves);
        assertEquals(1, leaves.size());
        Object ob = leaves.get(0);        
        assertSame(LeafBean.class, ob.getClass());
        assertEquals("foo", leaves.get(0).value);
    }


    public static interface GenericBean<T> {
        List<T> getSomeData();
    }

    public static abstract class GenericClass<T> implements GenericBean<T> {

    }


    public void testGenericInterface() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MrBeanModule());

        GenericBean<ListBean> bean = mapper.readValue("{\"someData\":[{\"leaves\":[{\"value\":\"foo\"}] },{\"leaves\":[{\"value\":\"foo\"}] }] }", new TypeReference<GenericBean<ListBean>>(){});
        assertNotNull(bean);

        for (ListBean subBean : bean.getSomeData()) {
            List<LeafBean> leaves = subBean.getLeaves();
            assertNotNull(leaves);
            assertEquals(1, leaves.size());
            Object ob = leaves.get(0);
            assertSame(LeafBean.class, ob.getClass());
            assertEquals("foo", leaves.get(0).value);
        }
    }

    public void testGenericClass() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MrBeanModule());

        GenericClass<ListBean> bean = mapper.readValue("{\"someData\":[{\"leaves\":[{\"value\":\"foo\"}] },{\"leaves\":[{\"value\":\"foo\"}] }] }", new TypeReference<GenericClass<ListBean>>(){});
        assertNotNull(bean);

        for (ListBean subBean : bean.getSomeData()) {
            List<LeafBean> leaves = subBean.getLeaves();
            assertNotNull(leaves);
            assertEquals(1, leaves.size());
            Object ob = leaves.get(0);
            assertSame(LeafBean.class, ob.getClass());
            assertEquals("foo", leaves.get(0).value);
        }
    }


}
