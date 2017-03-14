/*
* Copyright (c) FasterXML, LLC.
* Licensed under the Apache (Software) License, version 2.0.
*/

package com.fasterxml.jackson.module.paranamer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestJDKSerializability extends ParanamerTestBase
{
    static class Point {
        public int x, y;
    }
    
    public void testMapper() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper().registerModule(new ParanamerModule());
        // first: serialize as is
        byte[] ser = jdkSerialize(mapper);
        ObjectMapper m2 = jdkDeserialize(ser);

        // then use lightly, repeat
        byte[] rawPoint = m2.writeValueAsBytes(new Point());
        Point result = m2.readValue(rawPoint, Point.class);
        assertNotNull(result);

        ser = jdkSerialize(m2);
        ObjectMapper m3 = jdkDeserialize(ser);
        assertNotNull(m3);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
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
