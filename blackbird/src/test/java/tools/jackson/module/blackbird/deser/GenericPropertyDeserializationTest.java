package tools.jackson.module.blackbird.deser;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

public class GenericPropertyDeserializationTest extends BlackbirdTestBase
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

    final private ObjectMapper MAPPER = newObjectMapper();

    public void testGenericIssue4() throws Exception
    {
        MyClass result = MAPPER.readValue("{\"id\":\"foo\"}", MyClass.class);
        assertEquals("foo", result.getId());
    }
}
