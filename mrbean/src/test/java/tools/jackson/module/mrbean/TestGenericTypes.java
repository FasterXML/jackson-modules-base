package tools.jackson.module.mrbean;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

public class TestGenericTypes
    extends BaseTest
{
    public interface ListBean {
        public List<LeafBean> getLeaves();
    }

    public static class LeafBean {
        public String value;
    }

    public static interface GenericBean<T> {
        List<T> getSomeData();
    }

    public static abstract class GenericClass<T> implements GenericBean<T> {
    }

    public interface InterfaceWithReference8 {
        public AtomicReference<String> getOptionalString();
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    /**
     * Test simple leaf-level bean with 2 implied _beanProperties
     */
    @Test
    public void testSimpleInterface() throws Exception
    {
        ObjectMapper mapper = newMrBeanMapper();
        ListBean bean = mapper.readValue("{\"leaves\":[{\"value\":\"foo\"}] }", ListBean.class);
        assertNotNull(bean);
        List<LeafBean> leaves = bean.getLeaves();
        assertNotNull(leaves);
        assertEquals(1, leaves.size());
        Object ob = leaves.get(0);        
        assertSame(LeafBean.class, ob.getClass());
        assertEquals("foo", leaves.get(0).value);
    }

    @Test
    public void testGenericInterface() throws Exception
    {
        ObjectMapper mapper = newMrBeanMapper();

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

    @Test
    public void testGenericClass() throws Exception
    {
        ObjectMapper mapper = newMrBeanMapper();

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

    // for [mrbean#8]
    @Test
    public void testWithGenericReferenceType() throws Exception
    {
        ObjectMapper mapper = newMrBeanMapper();
        final String json = "{\"optionalString\":\"anotherValue\"}";

        InterfaceWithReference8 value = mapper.readValue(json, InterfaceWithReference8.class);
        assertNotNull(value);
    }
}
