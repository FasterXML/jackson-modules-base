package tools.jackson.module.afterburner.deser.merge;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonMerge;

import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.module.afterburner.AfterburnerTestBase;
import tools.jackson.databind.node.ArrayNode;

import static org.junit.jupiter.api.Assertions.*;

public class NodeMergeTest extends AfterburnerTestBase
{
    final static ObjectMapper MAPPER = afterburnerMapperBuilder()
            // 26-Oct-2016, tatu: Make sure we'll report merge problems by default
            .disable(MapperFeature.IGNORE_MERGE_FOR_UNMERGEABLE)
            .build();

    static class ObjectNodeWrapper {
        @JsonMerge
        public ObjectNode props = MAPPER.createObjectNode();
        {
            props.put("default", "enabled");
        }
    }

    static class ArrayNodeWrapper {
        @JsonMerge
        public ArrayNode list = MAPPER.createArrayNode();
        {
            list.add(123);
        }
    }

    /*
    /********************************************************
    /* Test methods
    /********************************************************
     */

    @Test
    public void testObjectNodeUpdateValue() throws Exception
    {
        ObjectNode base = MAPPER.createObjectNode();
        base.put("first", "foo");
        assertSame(base,
                MAPPER.readerForUpdating(base)
                .readValue(aposToQuotes("{'second':'bar', 'third':5, 'fourth':true}")));
        assertEquals(4, base.size());
        assertEquals("bar", base.path("second").asString());
        assertEquals("foo", base.path("first").asString());
        assertEquals(5, base.path("third").asInt());
        assertTrue(base.path("fourth").asBoolean());
    }

    @Test
    public void testObjectNodeMerge() throws Exception
    {
        ObjectNodeWrapper w = MAPPER.readValue(aposToQuotes("{'props':{'stuff':'xyz'}}"),
                ObjectNodeWrapper.class);
        assertEquals(2, w.props.size());
        assertEquals("enabled", w.props.path("default").asString());
        assertEquals("xyz", w.props.path("stuff").asString());
    }

    @Test
    public void testObjectDeepUpdate() throws Exception
    {
        ObjectNode base = MAPPER.createObjectNode();
        ObjectNode props = base.putObject("props");
        props.put("base", 123);
        props.put("value", 456);
        ArrayNode a = props.putArray("array");
        a.add(true);
        base.putNull("misc");
        assertSame(base,
                MAPPER.readerForUpdating(base)
                .readValue(aposToQuotes(
                        "{'props':{'value':true, 'extra':25.5, 'array' : [ 3 ]}}")));
        assertEquals(2, base.size());
        ObjectNode resultProps = (ObjectNode) base.get("props");
        assertEquals(4, resultProps.size());
        
        assertEquals(123, resultProps.path("base").asInt());
        assertTrue(resultProps.path("value").asBoolean());
        assertEquals(25.5, resultProps.path("extra").asDouble());
        JsonNode n = resultProps.get("array");
        assertEquals(ArrayNode.class, n.getClass());
        assertEquals(2, n.size());
        assertEquals(3, n.get(1).asInt());
    }

    @Test
    public void testArrayNodeUpdateValue() throws Exception
    {
        ArrayNode base = MAPPER.createArrayNode();
        base.add("first");
        assertSame(base,
                MAPPER.readerForUpdating(base)
                .readValue(aposToQuotes("['second',false,null]")));
        assertEquals(4, base.size());
        assertEquals("first", base.path(0).asString());
        assertEquals("second", base.path(1).asString());
        assertFalse(base.path(2).asBoolean());
        assertTrue(base.path(3).isNull());
    }

    @Test
    public void testArrayNodeMerge() throws Exception
    {
        ArrayNodeWrapper w = MAPPER.readValue(aposToQuotes("{'list':[456,true,{},  [], 'foo']}"),
                ArrayNodeWrapper.class);
        assertEquals(6, w.list.size());
        assertEquals(123, w.list.get(0).asInt());
        assertEquals(456, w.list.get(1).asInt());
        assertTrue(w.list.get(2).asBoolean());
        JsonNode n = w.list.get(3);
        assertTrue(n.isObject());
        assertEquals(0, n.size());
        n = w.list.get(4);
        assertTrue(n.isArray());
        assertEquals(0, n.size());
        assertEquals("foo", w.list.get(5).asString());
    }
}
