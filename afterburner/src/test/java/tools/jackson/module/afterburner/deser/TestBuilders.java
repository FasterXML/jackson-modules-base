package tools.jackson.module.afterburner.deser;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.module.afterburner.AfterburnerTestBase;

public class TestBuilders extends AfterburnerTestBase
{
    // [Issue#22]:
    static final class ThingBuilder
    {
        private String foo;

        public ThingBuilder withFoo(String str) {
            foo = str;
            return this;
        }

        public Thing build() {
            return new Thing(foo);
        }
    }

    @JsonDeserialize(builder=ThingBuilder.class)
    static class Thing {
        final String foo;
    
        Thing(String foo) {
            this.foo = foo;
        }
    }

    /*
    /**********************************************************
    /* Test methods, valid cases, non-deferred, no-mixins
    /**********************************************************
     */
    
    private final ObjectMapper MAPPER = newAfterburnerMapper();
    
    public void testSimpleBuilder() throws Exception
    {
        final Thing expected = new ThingBuilder().withFoo("bar").build();
        final Thing actual = MAPPER.readValue("{ \"foo\": \"bar\"}", Thing.class);
        
        assertNotNull(actual);
        assertNotNull(actual.foo);
        assertEquals(expected.foo, actual.foo);
    }

}
