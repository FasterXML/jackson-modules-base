package tools.jackson.module.afterburner.deser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;
import tools.jackson.databind.*;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class FinalFieldsTest extends AfterburnerTestBase
{
    static class Address {
        public int zip1, zip2;

        public Address() { }
        public Address(int z1, int z2) {
            zip1 = z1;
            zip2 = z2;
        }
    }
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class Organization
    {
        public final long id;
        public final String name;
        public final Address address;

        @JsonCreator
        public Organization(@JsonProperty("id") long id,
                @JsonProperty("name") String name,
                @JsonProperty("address") Address address)
        {
            this.id = id;
            this.name = name;
            this.address = address;
        }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    private final ObjectMapper MAPPER = afterburnerMapperBuilder()
            .enable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
            .build();

    @Test
    public void testFinalFields() throws Exception
    {
        String json = MAPPER.writeValueAsString(new Organization[] {
                new Organization(123L, "Corp", new Address(98040, 98021))
        });
        Organization[] result = MAPPER.readValue(json, Organization[].class);
        assertNotNull(result);
        assertEquals(1, result.length);
        assertNotNull(result[0]);
        assertNotNull(result[0].address);
        assertEquals(98021, result[0].address.zip2);
    }

    // For [Afterburner#42]

    @Test
    public void testFinalFields42() throws Exception
    {
        JsonAddress address = new JsonAddress(-1L, "line1", "line2", "city", "state", "zip", "locale", "timezone");
        JsonOrganization organization = new JsonOrganization(-1L, "name", address);
        String json = MAPPER.writeValueAsString(organization);
        assertNotNull(json);
        
        JsonOrganization result = MAPPER.readValue(json, JsonOrganization.class);
        assertNotNull(result);
    }
 
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class JsonAddress extends Resource<JsonAddress> {
        public final long id;
        public final String state;
        public final String timezone;
        public final String locale;
        public final String line1;
        public final String line2;
        public final String city;
        public final String zipCode;

        // Note: missing last 2 fields, to trigger problem
        @JsonCreator
        public JsonAddress(@JsonProperty("id") long id,
                           @JsonProperty("line1") String line1,
                           @JsonProperty("line2") String line2,
                           @JsonProperty("city") String city,
                           @JsonProperty("state") String state,
                           @JsonProperty("zipCode") String zipCode)
        {
            this.id = id;
            this.line1 = line1;
            this.line2 = line2;
            this.city = city;
            this.state = state;
            this.zipCode = zipCode;
            this.locale = null;
            this.timezone = null;
        }

        public JsonAddress(long id,
                           String line1,
                           String line2,
                           String city,
                           String state,
                           String zipCode,
                           String locale,
                           String timezone)
        {
            this.id = id;
            this.line1 = line1;
            this.line2 = line2;
            this.city = city;
            this.state = state;
            this.zipCode = zipCode;
            this.locale = locale;
            this.timezone = timezone;
        }
    }
 
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class JsonOrganization extends Resource<JsonOrganization> {
        public final long id;
        public final String name;
        public final JsonAddress address;
 
        @JsonCreator
        public JsonOrganization(@JsonProperty("id") long id,
                                @JsonProperty("name") String name,
                                @JsonProperty("address") JsonAddress address)
        {
            this.id = id;
            this.name = name;
            this.address = address;
        }
    }

    static class Resource<T> { }
}
