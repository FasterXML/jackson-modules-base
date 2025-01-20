package tools.jackson.module.afterburner.deser.jdk;

import java.io.*;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class AfterburnerModuleJDKSerializability97Test extends AfterburnerTestBase
{
    static class Point {
        public int x, y;
    }

    // 19-Jan-2025, tatu: Fails on 2.x, works on 3.0+
    // But also test that after light use, ser/deser works
    @Test
    public void testMapperAfterUse() throws Exception
    {
        ObjectMapper mapper = newAfterburnerMapper();

        // force use of mapper first
        _serDeserPointWith(mapper);

        // then freeze/thaw
        byte[] ser = jdkSerialize(mapper);
        ObjectMapper m3 = jdkDeserialize(ser);
        assertNotNull(m3);

        _serDeserPointWith(m3);
    }

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
