package com.fasterxml.jackson.module.jaxb.deser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;

import javax.activation.DataHandler;
import javax.activation.DataSource;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;

import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;

/**
 * @author Ryan Heaton
 */
public class DataHandlerDeserializer
    extends StdScalarDeserializer<DataHandler>
{
    public DataHandlerDeserializer() { super(DataHandler.class); }

    @Override
    public DataHandler deserialize(JsonParser p, DeserializationContext ctxt)
        throws JacksonException
    {
        final byte[] value = p.getBinaryValue();
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
