package com.fasterxml.jackson.module.blackbird.ser;

import java.util.Arrays;
import java.util.Iterator;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;
import com.fasterxml.jackson.module.blackbird.deser.TestInaccessibleSetterTypes.ChildFirstLoader;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.Level;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.LevelBean;
import com.fasterxml.jackson.module.blackbird.deser.shadowed.LevelBeanBase;
import com.fasterxml.jackson.module.blackbird.ser.otherpackage.HiddenGetterBeanBase;

import static org.junit.jupiter.api.Assertions.*;

// Getters whose generated accessor would fail when called, even though reflection
// can call them, must be left to reflection (as with setters, see
// `TestInaccessibleSetterTypes`)
public class TestInaccessibleGetterTypes extends BlackbirdTestBase
{
    // a JDK `javax.*` return type comes from another loader, but this class's
    // loader resolved it, as the generated getter will
    public static class OtherLoaderReturnType
    {
        public XMLGregorianCalendar getWhen() throws Exception {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar("2020-01-02T03:04:05Z");
        }
    }

    public static final class HiddenGetterBean extends HiddenGetterBeanBase { }

    private final ObjectMapper MAPPER = newObjectMapper();

    // `unreflect` refuses some inherited getters that reflection can call; these used
    // to fail the whole serializer
    @Test
    public void testInheritedGetterFromAnotherPackage() throws Exception
    {
        assertEquals(a2q("{'y':3}"), MAPPER.writeValueAsString(new HiddenGetterBean()));
        assertEquals(BeanPropertyWriter.class, _findWriter(HiddenGetterBean.class, "y").getClass());
    }

    // The bean's loader has its own copy of `Level`, so a generated getter would
    // resolve that one and fail to link to the inherited getter returning `Level`
    @Test
    public void testShadowedReturnType() throws Exception
    {
        ChildFirstLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                Arrays.asList(Level.class, LevelBean.class));
        Class<?> bean = Class.forName(LevelBean.class.getName(), true, loader);
        assertNotSame(Level.class, Class.forName(Level.class.getName(), false, loader));

        LevelBeanBase value = (LevelBeanBase) bean.getConstructor().newInstance();
        value.setLevel(Level.HIGH);
        assertEquals(a2q("{'level':'HIGH'}"), MAPPER.writeValueAsString(value));
        assertEquals(BeanPropertyWriter.class, _findWriter(bean, "level").getClass());
    }

    @Test
    public void testOtherLoaderReturnTypeStaysOptimized() throws Exception
    {
        assertNotEquals(BeanPropertyWriter.class, _findWriter(OtherLoaderReturnType.class, "when").getClass());
        assertEquals(newVanillaJSONMapper().writeValueAsString(new OtherLoaderReturnType()),
                MAPPER.writeValueAsString(new OtherLoaderReturnType()));
    }

    private PropertyWriter _findWriter(Class<?> type, String name) throws Exception
    {
        BeanSerializerBase ser = (BeanSerializerBase) MAPPER.getSerializerProviderInstance()
                .findValueSerializer(type);
        for (Iterator<PropertyWriter> it = ser.properties(); it.hasNext(); ) {
            PropertyWriter writer = it.next();
            if (writer.getName().equals(name)) {
                return writer;
            }
        }
        throw new AssertionError("No property '" + name + "' for " + type.getName());
    }
}
