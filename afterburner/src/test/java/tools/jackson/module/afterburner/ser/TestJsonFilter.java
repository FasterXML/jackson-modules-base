package tools.jackson.module.afterburner.ser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.databind.*;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.ser.FilterProvider;
import tools.jackson.databind.ser.std.SimpleBeanPropertyFilter;
import tools.jackson.databind.ser.std.SimpleFilterProvider;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for verifying that bean property filtering using JsonFilter
 * works as expected.
 */
public class TestJsonFilter extends AfterburnerTestBase
{
    @JsonFilter("RootFilter")
    @JsonPropertyOrder({"a", "b"})
    static class Bean {
        public String a = "a";
        public String b = "b";
    }

    static class Pod
    {
        protected String username;

//        @JsonProperty(value = "user_password")
        protected String userPassword;

        public String getUsername() {
            return username;
        }

        public void setUsername(String value) {
            this.username = value;
        }

        @JsonIgnore
        @JsonProperty(value = "user_password")
        public java.lang.String getUserPassword() {
            return userPassword;
        }

        @JsonProperty(value = "user_password")
        public void setUserPassword(String value) {
            this.userPassword = value;
        }
    }    

    // [Issue#306]: JsonFilter for properties, too!

    @JsonPropertyOrder(alphabetic=true)
    static class FilteredProps
    {
        // will default to using "RootFilter", only including 'a'
        public Bean first = new Bean();

        // but minimal includes 'b'
        @JsonFilter("b")
        public Bean second = new Bean();
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    private final ObjectMapper MAPPER = newAfterburnerMapper();
    
    @Test
    public void testSimpleInclusionFilter() throws Exception
    {
        FilterProvider prov = new SimpleFilterProvider().addFilter("RootFilter",
                SimpleBeanPropertyFilter.filterOutAllExcept("a"));
        assertEquals("{\"a\":\"a\"}", MAPPER.writer(prov).writeValueAsString(new Bean()));

        ObjectMapper mapper = afterburnerMapperBuilder()
                .filterProvider(prov)
                .build();
        assertEquals("{\"a\":\"a\"}", mapper.writeValueAsString(new Bean()));
    }

    @Test
    public void testSimpleExclusionFilter() throws Exception
    {
        FilterProvider prov = new SimpleFilterProvider().addFilter("RootFilter",
                SimpleBeanPropertyFilter.serializeAllExcept("a"));
        assertEquals("{\"b\":\"b\"}", MAPPER.writer(prov).writeValueAsString(new Bean()));
    }

    // should handle missing case gracefully
    @Test
    public void testMissingFilter() throws Exception
    {
        // First: default behavior should be to throw an exception
        try {
            MAPPER.writeValueAsString(new Bean());
            fail("Should have failed without configured filter");
        } catch (InvalidDefinitionException e) { // should be resolved to a MappingException (internally may be something else)
            verifyException(e, "Cannot resolve PropertyFilter with id 'RootFilter'");
        }
        
        // but when changing behavior, should work difference
        SimpleFilterProvider fp = new SimpleFilterProvider().setFailOnUnknownId(false);
        ObjectMapper mapper = afterburnerMapperBuilder()
                .filterProvider(fp)
                .build();
        String json = mapper.writeValueAsString(new Bean());
        assertEquals("{\"a\":\"a\",\"b\":\"b\"}", json);
    }

    @Test
    public void testDefaultFilter() throws Exception
    {
        FilterProvider prov = new SimpleFilterProvider().setDefaultFilter(SimpleBeanPropertyFilter.filterOutAllExcept("b"));
        assertEquals("{\"b\":\"b\"}", MAPPER.writer(prov).writeValueAsString(new Bean()));
    }
    
    // [modules-base#89] combining @JsonIgnore, @JsonProperty
    @Test
    public void testIssue89() throws Exception
    {
        Pod pod = new Pod();
        pod.username = "Bob";
        pod.userPassword = "s3cr3t!";

        String json = MAPPER.writeValueAsString(pod);

        assertEquals("{\"username\":\"Bob\"}", json);

        Pod pod2 = MAPPER.readValue("{\"username\":\"Bill\",\"user_password\":\"foo!\"}", Pod.class);
        assertEquals("Bill", pod2.username);
        assertEquals("foo!", pod2.userPassword);
    }

    // Wrt [modules-base#306]
    @Test
    public void testFilterOnProperty() throws Exception
    {
        FilterProvider prov = new SimpleFilterProvider()
            .addFilter("RootFilter", SimpleBeanPropertyFilter.filterOutAllExcept("a"))
            .addFilter("b", SimpleBeanPropertyFilter.filterOutAllExcept("b"));

        assertEquals("{\"first\":{\"a\":\"a\"},\"second\":{\"b\":\"b\"}}",
                MAPPER.writer(prov).writeValueAsString(new FilteredProps()));
    }
}
