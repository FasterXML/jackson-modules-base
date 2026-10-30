package tools.jackson.module.blackbird.ser;

import java.util.Arrays;
import java.util.Iterator;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.module.blackbird.BlackbirdTestBase;
import tools.jackson.module.blackbird.deser.TestInaccessibleSetterTypes.ChildFirstLoader;
import tools.jackson.module.blackbird.deser.shadowed.Level;
import tools.jackson.module.blackbird.deser.shadowed.LevelBean;
import tools.jackson.module.blackbird.deser.shadowed.LevelBeanBase;
import tools.jackson.module.blackbird.ser.otherpackage.HiddenGetterBeanBase;

import static org.junit.jupiter.api.Assertions.*;
import static tools.jackson.module.blackbird.deser.TestInaccessibleSetterTypes.READS;
import static tools.jackson.module.blackbird.deser.TestInaccessibleSetterTypes.load;
import static tools.jackson.module.blackbird.deser.TestInaccessibleSetterTypes.lookupInBean;

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
        for (boolean reads : READS) {
            ChildFirstLoader loader = new ChildFirstLoader(getClass().getClassLoader(),
                    Arrays.asList(Level.class, LevelBean.class));
            Class<?> bean = load(loader, LevelBean.class, reads);
            assertNotSame(Level.class, Class.forName(Level.class.getName(), false, loader));

            LevelBeanBase value = (LevelBeanBase) bean.getConstructor().newInstance();
            value.setLevel(Level.HIGH);
            assertEquals(a2q("{'level':'HIGH'}"), MAPPER.writeValueAsString(value));
            // (see `TestInaccessibleSetterTypes.READS` for when it can stay optimized)
            assertEquals(lookupInBean(bean),
                    BeanPropertyWriter.class == _findWriter(bean, "level").getClass(), "reads=" + reads);
        }
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
        BeanSerializerBase ser = (BeanSerializerBase) MAPPER._serializationContext()
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
