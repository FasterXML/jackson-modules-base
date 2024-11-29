package tools.jackson.module.jakarta.xmlbind.ser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import jakarta.activation.DataHandler;

import tools.jackson.core.*;
import tools.jackson.core.exc.JacksonIOException;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;

public class DataHandlerSerializer extends StdSerializer<DataHandler>
{
    public DataHandlerSerializer() { super(DataHandler.class); }
    
    @Override
    public void serialize(DataHandler value, JsonGenerator g, SerializationContext ctxt)
        throws JacksonException
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

        try (InputStream in = value.getInputStream()) {
            int len = in.read(buffer);
            while (len > 0) {
                out.write(buffer, 0, len);
                len = in.read(buffer);
            }
        } catch (IOException e) {
            throw JacksonIOException.construct(e);
        }
        g.writeBinary(out.toByteArray());
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint)
    {
        if (visitor != null) {
            JsonArrayFormatVisitor v2 = visitor.expectArrayFormat(typeHint);
            if (v2 != null) {
                v2.itemsFormat(JsonFormatTypes.STRING);
            }
        }
    }
}
