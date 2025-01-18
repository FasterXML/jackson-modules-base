package tools.jackson.module.afterburner.ser;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class GenericPropertySerializationTest extends AfterburnerTestBase
{
    public static abstract class AbstractMyClass<ID> {

        private ID id;

        AbstractMyClass(ID id) {
            setId(id);
        }

        public ID getId() {
            return id;
        }

        public void setId(ID id) {
            this.id = id;
        }
    }

    public static class MyClass extends AbstractMyClass<String> {
        public MyClass(String id) {
            super(id);
        }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    final private ObjectMapper MAPPER = newAfterburnerMapper();

    @Test
    public void testGenericIssue4() throws Exception
    {
        MyClass input = new MyClass("foo");
        String json = MAPPER.writeValueAsString(input);
        assertEquals("{\"id\":\"foo\"}", json);
    }
}
