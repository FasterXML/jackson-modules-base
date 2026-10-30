package tools.jackson.module.blackbird.ser;

import java.util.Arrays;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
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

// Getters that generated code could easily get wrong, though reflection calls them
// fine; generated writers must handle them as stock does (as with setters, see
// `TestInaccessibleSetterTypes`)
public class TestInaccessibleGetterTypes extends BlackbirdTestBase
{
    // a JDK `javax.*` return type, from another loader
    public static class OtherLoaderReturnType
    {
        public XMLGregorianCalendar getWhen() throws Exception {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar("2020-01-02T03:04:05Z");
        }
    }

    public static final class HiddenGetterBean extends HiddenGetterBeanBase { }

    private final ObjectMapper MAPPER = newObjectMapper();

    // An inherited getter that only reflection can call
    @Test
    public void testInheritedGetterFromAnotherPackage() throws Exception
    {
        assertEquals(a2q("{'y':3}"), MAPPER.writeValueAsString(new HiddenGetterBean()));
        assertGeneratedWriter(MAPPER, HiddenGetterBean.class);
    }

    // The bean's loader has its own copy of `Level`, so code resolving it from there
    // would fail to link to the inherited getter, which returns the parent's
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
            assertGeneratedWriter(MAPPER, bean);
        }
    }

    @Test
    public void testOtherLoaderReturnTypeStaysOptimized() throws Exception
    {
        assertGeneratedWriter(MAPPER, OtherLoaderReturnType.class);
        assertEquals(newVanillaJSONMapper().writeValueAsString(new OtherLoaderReturnType()),
                MAPPER.writeValueAsString(new OtherLoaderReturnType()));
    }
}
