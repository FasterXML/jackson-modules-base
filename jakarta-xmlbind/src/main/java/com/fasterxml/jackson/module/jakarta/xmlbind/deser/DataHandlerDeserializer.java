package com.fasterxml.jackson.module.jakarta.xmlbind.deser;

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

public class DataHandlerDeserializer
    extends StdScalarDeserializer<DataHandler>
{
    private static final long serialVersionUID = 1L;

    public DataHandlerDeserializer() { super(DataHandler.class); }

    @Override
    public DataHandler deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, JsonProcessingException
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
