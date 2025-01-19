package tools.jackson.module.jakarta.xmlbind.misc;

import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.xml.bind.annotation.XmlElement;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.module.jakarta.xmlbind.ModuleTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSerializationInclusion extends ModuleTestBase
{
    static class Data {
        private final List<Object> stuff = new java.util.ArrayList<Object>();

        @XmlElement
        public List<Object> getStuff() {
            return stuff;
        }
    }

    @Test
    public void testIssue39() throws Exception
    {
        // First: use plain JAXB introspector:
        _testInclusion(getJaxbMapperBuilder());
        // and then combination ones
        _testInclusion(getJaxbAndJacksonMapperBuilder());
        _testInclusion(getJacksonAndJaxbMapperBuilder());
    }

    private void _testInclusion(MapperBuilder<?,?> builder) throws Exception
    {
        ObjectMapper mapper = builder.changeDefaultPropertyInclusion(
                incl -> incl.withValueInclusion(JsonInclude.Include.NON_EMPTY))
                .build();
        String json = mapper.writeValueAsString(new Data());
        assertEquals("{}", json);
    }
}
