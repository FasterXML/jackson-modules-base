package tools.jackson.module.afterburner.deser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [modules-base#339]: @JsonView deserialization filtering broken with AfterburnerModule
public class DeserializeWithViewTest extends AfterburnerTestBase
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
        ObjectMapper mapper = newAfterburnerMapper();
        String json = "{\"activeField\":\"hello\",\"ignoredField\":\"hopla\"}";

        ViewBean bean = mapper.readerWithView(ViewA.class)
                .forType(ViewBean.class)
                .readValue(json);

        assertEquals("hello", bean.activeField);
        assertNull(bean.ignoredField,
                "@JsonView(ViewB.class) property should not be deserialized with ViewA active");
    }

    @Test
    public void testReaderWithoutView() throws Exception
    {
        ObjectMapper mapper = newAfterburnerMapper();
        String json = "{\"activeField\":\"hello\",\"ignoredField\":\"hopla\"}";

        // Without a view, both fields should be deserialized
        ViewBean bean = mapper.readValue(json, ViewBean.class);

        assertEquals("hello", bean.activeField);
        assertEquals("hopla", bean.ignoredField);
    }

    // Fast paths must also honor the active view when updating an existing
    // bean, and with enough properties for `SuperSonicBeanDeserializer`
    // (as opposed to `SuperSonicUnrolledDeserializer` used above)

    static class WideViewBean {
        @JsonView(ViewA.class)
        public String a;
        @JsonView(ViewB.class)
        public String b;
        @JsonView(ViewA.class)
        public String c;
        @JsonView(ViewB.class)
        public String d;
        @JsonView(ViewA.class)
        public String e;
        @JsonView(ViewB.class)
        public String f;
        @JsonView(ViewA.class)
        public String g;
        @JsonView(ViewB.class)
        public String h;
    }

    private final static String WIDE_JSON = a2q(
            "{'a':'1','b':'2','c':'3','d':'4','e':'5','f':'6','g':'7','h':'8'}");

    // Verify that beans really use the deserializers tests expect to cover
    @Test
    public void testDeserializerTypes() throws Exception
    {
        JsonMapper mapper = afterburnerMapperBuilder().build();
        assertInstanceOf(SuperSonicUnrolledDeserializer.class, _deserializerFor(mapper, ViewBean.class));
        assertInstanceOf(SuperSonicBeanDeserializer.class, _deserializerFor(mapper, WideViewBean.class));
    }

    private ValueDeserializer<Object> _deserializerFor(JsonMapper mapper, Class<?> type)
    {
        return mapper._deserializationContext()
                .findRootValueDeserializer(mapper.constructType(type));
    }

    @Test
    public void testUpdateWithView() throws Exception
    {
        ObjectMapper mapper = newAfterburnerMapper();
        String json = "{\"activeField\":\"hello\",\"ignoredField\":\"hopla\"}";

        ViewBean bean = new ViewBean();
        ViewBean result = mapper.readerWithView(ViewA.class)
                .withValueToUpdate(bean)
                .readValue(json);

        assertSame(bean, result);
        assertEquals("hello", bean.activeField);
        assertNull(bean.ignoredField);
    }

    @Test
    public void testUpdateWithoutView() throws Exception
    {
        ObjectMapper mapper = newAfterburnerMapper();
        String json = "{\"activeField\":\"hello\",\"ignoredField\":\"hopla\"}";

        ViewBean bean = new ViewBean();
        mapper.readerForUpdating(bean).readValue(json);

        assertEquals("hello", bean.activeField);
        assertEquals("hopla", bean.ignoredField);
    }

    @Test
    public void testWideBeanWithView() throws Exception
    {
        WideViewBean bean = newAfterburnerMapper().readerWithView(ViewA.class)
                .forType(WideViewBean.class)
                .readValue(WIDE_JSON);

        _verifyWideWithViewA(bean);
    }

    @Test
    public void testWideBeanWithoutView() throws Exception
    {
        WideViewBean bean = newAfterburnerMapper().readValue(WIDE_JSON, WideViewBean.class);

        _verifyWideWithoutView(bean);
    }

    @Test
    public void testWideBeanUpdateWithView() throws Exception
    {
        WideViewBean bean = new WideViewBean();
        newAfterburnerMapper().readerWithView(ViewA.class)
                .withValueToUpdate(bean)
                .readValue(WIDE_JSON);

        _verifyWideWithViewA(bean);
    }

    @Test
    public void testWideBeanUpdateWithoutView() throws Exception
    {
        WideViewBean bean = new WideViewBean();
        newAfterburnerMapper().readerForUpdating(bean).readValue(WIDE_JSON);

        _verifyWideWithoutView(bean);
    }

    private void _verifyWideWithViewA(WideViewBean bean)
    {
        assertEquals("1", bean.a);
        assertNull(bean.b);
        assertEquals("3", bean.c);
        assertNull(bean.d);
        assertEquals("5", bean.e);
        assertNull(bean.f);
        assertEquals("7", bean.g);
        assertNull(bean.h);
    }

    private void _verifyWideWithoutView(WideViewBean bean)
    {
        assertEquals("1", bean.a);
        assertEquals("2", bean.b);
        assertEquals("3", bean.c);
        assertEquals("4", bean.d);
        assertEquals("5", bean.e);
        assertEquals("6", bean.f);
        assertEquals("7", bean.g);
        assertEquals("8", bean.h);
    }
}
