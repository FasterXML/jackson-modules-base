package tools.jackson.module.afterburner.deser;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class GenericPropertyDeserializationTest extends AfterburnerTestBase
{
    static abstract class AbstractMyClass<ID> {
        private ID id;

        AbstractMyClass() { }

        public ID getId() {
            return id;
        }

        public void setId(ID id) {
            this.id = id;
        }
    }

    public static class MyClass extends AbstractMyClass<String> {
        public MyClass() { }
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
        MyClass result = MAPPER.readValue("{\"id\":\"foo\"}", MyClass.class);
        assertEquals("foo", result.getId());
    }
}
