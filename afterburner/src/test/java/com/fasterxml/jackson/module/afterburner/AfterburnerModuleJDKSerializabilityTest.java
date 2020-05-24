package com.fasterxml.jackson.module.afterburner;

import java.io.*;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AfterburnerModuleJDKSerializabilityTest extends AfterburnerTestBase
{
    static class Point {
        public int x, y;
    }

    // First: verify that newly constructed module (registered to Mapper)
    // can be JDK serialized, deserialized
    public void testMapperWithoutUse() throws Exception
    {
        ObjectMapper mapper = newObjectMapper();
        byte[] ser = jdkSerialize(mapper);
        ObjectMapper m2 = jdkDeserialize(ser);
        assertNotNull(m2);

        // and that it can be used successfully
        _serDeserPointWith(m2);
    }

    // 24-May-2020, tatu: With 2.x, this can't succeed it seems
    
    // But also test that after light use, ser/deser works
    /*
    public void testMapperAfterUse() throws Exception
    {
        ObjectMapper mapper = newObjectMapper();

        // force use of mapper first
        _serDeserPointWith(mapper);

        // then freeze/thaw
        byte[] ser = jdkSerialize(mapper);
        ObjectMapper m3 = jdkDeserialize(ser);
        assertNotNull(m3);

        _serDeserPointWith(m3);
    }
    */

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    private void _serDeserPointWith(ObjectMapper mapper) throws Exception
    {
        final Point input = new Point();
        byte[] rawPoint = mapper.writeValueAsBytes(input);
        Point result = mapper.readValue(rawPoint, Point.class);
        assertNotNull(result);
        assertEquals(input.x, result.x);
        assertEquals(input.y, result.y);
    }

    protected byte[] jdkSerialize(Object o) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1000);
        ObjectOutputStream obOut = new ObjectOutputStream(bytes);
        obOut.writeObject(o);
        obOut.close();
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    protected <T> T jdkDeserialize(byte[] raw) throws IOException
    {
        ObjectInputStream objIn = new ObjectInputStream(new ByteArrayInputStream(raw));
        try {
            return (T) objIn.readObject();
        } catch (ClassNotFoundException e) {
            fail("Missing class: "+e.getMessage());
            return null;
        } finally {
            objIn.close();
        }
    }
}
