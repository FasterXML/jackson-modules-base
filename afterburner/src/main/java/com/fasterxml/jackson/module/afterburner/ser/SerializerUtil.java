package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.util.ClassUtil;

/**
 * Helper class that contains utility methods needed by various other classes
 * in this package.
 *
 * @since 2.12
 */
class SerializerUtil
{
    /**
     * Helper method used to check whether given serializer is the default
     * serializer implementation: this is necessary to avoid overriding other
     * kinds of serializers.
     */
    public static boolean isDefaultSerializer(JsonSerializer<?> ser)
    {
        if (ser == null) {
            return true;
        }
        if (ClassUtil.isJacksonStdImpl(ser)) {
            // 20-Nov-2020, tatu: As per [modules-base#117], need to consider
            //   one standard serializer that should not be replaced...
            if (ser instanceof ToStringSerializer) {
                return false;
            }
            return true;
        }
        return false;
    }
}
