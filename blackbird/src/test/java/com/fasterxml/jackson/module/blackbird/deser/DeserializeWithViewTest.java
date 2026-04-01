package com.fasterxml.jackson.module.blackbird.deser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [modules-base#339]: @JsonView deserialization filtering broken with BlackbirdModule
public class DeserializeWithViewTest extends BlackbirdTestBase
{
    static class ViewA { }
    static class ViewB { }

    static class ViewBean {
        @JsonView(ViewA.class)
        public String activeField;

        @JsonView(ViewB.class)
        public String ignoredField;
    }

    @Test
    public void testReaderWithView() throws Exception
    {
        ObjectMapper mapper = newObjectMapper();
        String json = "{\"activeField\":\"hello\",\"ignoredField\":\"hopla\"}";

        ViewBean bean = mapper.readerWithView(ViewA.class)
                .readValue(json, ViewBean.class);

        assertEquals("hello", bean.activeField);
        assertNull(bean.ignoredField,
                "@JsonView(ViewB.class) property should not be deserialized with ViewA active");
    }

    @Test
    public void testReaderWithoutView() throws Exception
    {
        ObjectMapper mapper = newObjectMapper();
        String json = "{\"activeField\":\"hello\",\"ignoredField\":\"hopla\"}";

        // Without a view, both fields should be deserialized
        ViewBean bean = mapper.readValue(json, ViewBean.class);

        assertEquals("hello", bean.activeField);
        assertEquals("hopla", bean.ignoredField);
    }
}
