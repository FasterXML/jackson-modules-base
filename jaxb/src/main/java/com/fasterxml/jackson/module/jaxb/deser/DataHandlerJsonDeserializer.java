package com.fasterxml.jackson.module.jaxb.deser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;

import javax.activation.DataHandler;
import javax.activation.DataSource;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

/**
 * @author Ryan Heaton
 */
public class DataHandlerJsonDeserializer
    extends StdScalarDeserializer<DataHandler>
{
    private static final long serialVersionUID = 1L;

    public DataHandlerJsonDeserializer() { super(DataHandler.class); }

    @Override
    public DataHandler deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException
    {
        final byte[] value = jp.getBinaryValue();
        return new DataHandler(new DataSource() {
            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(value);
            }

            @Override
            public OutputStream getOutputStream() throws IOException {
                throw new IOException();
            }

            @Override
            public String getContentType() {
                return "application/octet-stream";
            }

            @Override
            public String getName() {
                return "json-binary-data";
            }
        });
    }
}
