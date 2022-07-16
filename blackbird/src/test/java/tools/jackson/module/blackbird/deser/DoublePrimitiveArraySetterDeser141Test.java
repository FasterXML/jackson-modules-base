package tools.jackson.module.blackbird.deser;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

public class DoublePrimitiveArraySetterDeser141Test extends BlackbirdTestBase
{
    static class Foo141 {
        double[] bar;

        public double[] getBar() {
            return bar;
        }

        public Foo141 setBar(@JsonProperty("bar") double[] bar) {
            this.bar = bar;
            return this;
        }
    }

    private final ObjectMapper MAPPER = newObjectMapper();

    public void testDoubleArraySetter() throws Exception
    {
        Foo141 foo = new Foo141().setBar(new double[] { 2.0, 0.25 });
        String serialized = MAPPER.writeValueAsString(foo);
        Foo141 foo2 = MAPPER.readValue(serialized, Foo141.class);

        assertEquals(2, foo2.bar.length);
        assertEquals(0.25, foo2.bar[1]);
    }
}
