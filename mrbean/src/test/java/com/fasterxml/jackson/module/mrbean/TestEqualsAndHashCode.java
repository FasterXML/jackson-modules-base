package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertNotEquals;

public class TestEqualsAndHashCode extends BaseTest {

    public interface ReadOnlyBean {
        String getField();
    }

    public interface ListBean {
        public List<LeafBean> getLeaves();
    }

    public static class LeafBean {
        public String value;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LeafBean leafBean = (LeafBean) o;

            return value != null ? value.equals(leafBean.value) : leafBean.value == null;
        }

        @Override
        public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }
    }

    public static interface GenericBean<T> {
        List<T> getSomeData();
    }


    public void testReadOnlyBeanSameNonNullInput() throws IOException {
        final ObjectMapper mapper = newMrBeanMapper();
        
        final String input = "{\"field\":\"testing\"}";

        final ReadOnlyBean bean1 = mapper.readValue(input, ReadOnlyBean.class);
        final ReadOnlyBean bean2 = mapper.readValue(input, ReadOnlyBean.class);

        assertEqualsAndHashCode(bean1, bean2);
    }

    public void testReadOnlyBeanSameNullInput() throws IOException {
        final ObjectMapper mapper = newMrBeanMapper();

        final String input = "{\"field\":null}";

        final ReadOnlyBean bean1 = mapper.readValue(input, ReadOnlyBean.class);
        final ReadOnlyBean bean2 = mapper.readValue(input, ReadOnlyBean.class);

        assertEqualsAndHashCode(bean1, bean2);
    }

    public void testReadOnlyBeanDifferentInput() throws IOException {
        final ObjectMapper mapper = newMrBeanMapper();

        final String input1 = "{\"field\":\"testing\"}";
        final ReadOnlyBean bean1 = mapper.readValue(input1, ReadOnlyBean.class);

        final String input2 = "{\"field\":\"testing2\"}";
        final ReadOnlyBean bean2 = mapper.readValue(input2, ReadOnlyBean.class);

        assertNeitherEqualsNorHashCode(bean1, bean2);
    }

    public void testGenericBeanSameInput() throws IOException {
        final ObjectMapper mapper = newMrBeanMapper();

        final String input = "{\"someData\":[{\"leaves\":[{\"value\":\"foo\"}] },{\"leaves\":[{\"value\":\"foo\"}] }]}";

        final GenericBean<ListBean> bean1 = mapper.readValue(input, new TypeReference<GenericBean<ListBean>>(){});
        final GenericBean<ListBean> bean2 = mapper.readValue(input, new TypeReference<GenericBean<ListBean>>(){});

        assertEqualsAndHashCode(bean1, bean2);
    }

    public void testGenericBeanDifferentInput() throws IOException {
        final ObjectMapper mapper = newMrBeanMapper();

        final String input1 = "{\"someData\":[{\"leaves\":[{\"value\":\"foo\"}] },{\"leaves\":[{\"value\":\"foo\"}] }]}";
        final GenericBean<ListBean> bean1 = mapper.readValue(input1, new TypeReference<GenericBean<ListBean>>(){});

        final String input2 = "{\"someData\":[{\"leaves\":[{\"value\":\"foo\"}] }]}";
        final GenericBean<ListBean> bean2 = mapper.readValue(input2, new TypeReference<GenericBean<ListBean>>(){});

        assertNeitherEqualsNorHashCode(bean1, bean2);
    }

    private void assertEqualsAndHashCode(Object o1, Object o2) {
        assertEquals(o1, o2);
        assertEquals(o1.hashCode(), o2.hashCode());
    }

    private void assertNeitherEqualsNorHashCode(Object instance1, Object instance2) {
        assertNotEquals(instance1, instance2);
        assertNotEquals(instance1.hashCode(), instance2.hashCode());
    }
}
