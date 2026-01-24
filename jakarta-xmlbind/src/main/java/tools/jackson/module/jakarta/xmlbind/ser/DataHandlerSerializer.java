package tools.jackson.module.jakarta.xmlbind.ser;

import java.io.IOException;
import java.io.InputStream;

import jakarta.activation.DataHandler;

import tools.jackson.core.*;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.core.type.WritableTypeId;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsontype.TypeSerializer;

public class DataHandlerSerializer extends StdSerializer<DataHandler>
{
    public DataHandlerSerializer() { super(DataHandler.class); }

    @Override
    public void serialize(DataHandler value, JsonGenerator g, SerializationContext ctxt)
        throws JacksonException
    {
        _writePayload(value, g, ctxt);
    }

    // Copied from `jackson-databind` `ByteArraySerializer`
    @Override
    public void serializeWithType(DataHandler value, JsonGenerator g, SerializationContext ctxt,
            TypeSerializer typeSer)
        throws JacksonException
    {
        WritableTypeId typeIdDef = typeSer.writeTypePrefix(g, ctxt,
                typeSer.typeId(value, JsonToken.VALUE_EMBEDDED_OBJECT));
        _writePayload(value, g, ctxt);
        typeSer.writeTypeSuffix(g, ctxt, typeIdDef);
    }
    
    protected void _writePayload(DataHandler value, JsonGenerator g, SerializationContext ctxt)
    {
        try (InputStream in = value.getInputStream()) {
            g.writeBinary(ctxt.getConfig().getBase64Variant(), in, -1);
        } catch (IOException e) {
            throw JacksonIOException.construct(e);
        }
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
