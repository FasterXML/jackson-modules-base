package com.fasterxml.jackson.module.afterburner.roundtrip;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

// for [issue#47]
public class AbstractSettersTest extends AfterburnerTestBase
{
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type")
        @JsonSubTypes({
            @JsonSubTypes.Type(value = FooImpl1.class, name = "impl1") })
        @JsonInclude(JsonInclude.Include.NON_NULL)
    public interface FooBase extends Cloneable
    {
        String getId();
        void setId(String id);

        FooBase clone() throws CloneNotSupportedException;
    }

    static class FooImpl1 implements FooBase {
        protected String id, revision, category;

        @Override
        public String getId() { return id; }

        @Override
        public void setId(String id) { this.id = id; }

        @Override
        public FooBase clone() throws CloneNotSupportedException {
            return this;
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = newAfterburnerMapper();
    
    public void testSimpleConstructor() throws Exception
    {
        FooImpl1 item = new FooImpl1();
        String string = MAPPER.writer()
                .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .writeValueAsString(item);
        FooBase read = MAPPER.readValue(string, FooBase.class);
        assertNotNull(read);
    }
}
