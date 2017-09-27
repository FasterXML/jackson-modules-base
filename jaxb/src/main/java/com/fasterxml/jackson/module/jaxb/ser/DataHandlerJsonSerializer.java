package com.fasterxml.jackson.module.jaxb.ser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.activation.DataHandler;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;

public class DataHandlerJsonSerializer extends StdSerializer<DataHandler>
{
    private static final long serialVersionUID = 1L;

    public DataHandlerJsonSerializer() { super(DataHandler.class); }
    
    @Override
    public void serialize(DataHandler value, JsonGenerator jgen, SerializerProvider provider)
        throws IOException, JsonProcessingException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        /* for copy-through, a small buffer should suffice: ideally
         * we might want to reuse a generic byte buffer, but for now
         * there's no serializer context to hold them.
         * 
         * Also: it'd be nice not to have buffer all data, but use a
         * streaming output. But currently JsonGenerator won't allow
         * that.
         */
        byte[] buffer = new byte[1024 * 4];
        InputStream in = value.getInputStream();
        int len = in.read(buffer);
        while (len > 0) {
            out.write(buffer, 0, len);
            len = in.read(buffer);
        }
        in.close();
        jgen.writeBinary(out.toByteArray());
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint)
        throws JsonMappingException
    {
        if (visitor != null) {
            JsonArrayFormatVisitor v2 = visitor.expectArrayFormat(typeHint);
            if (v2 != null) {
                v2.itemsFormat(JsonFormatTypes.STRING);
            }
        }
    }
}
