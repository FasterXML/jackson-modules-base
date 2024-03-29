package com.fasterxml.jackson.module.androidrecord.failing;

import com.android.tools.r8.RecordTag;
import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;
import com.fasterxml.jackson.module.androidrecord.BaseMapTest;

import java.util.Objects;

public class RecordBasicsFailingTest extends BaseMapTest
{
  static final class RecordWithRename extends RecordTag {
    private final int id;
    @JsonProperty("rename")
    private final String name;

    RecordWithRename(int id, @JsonProperty("rename") String name) {
      this.id = id;
      this.name = name;
    }

    public int id() {
      return id;
    }

    @JsonProperty("rename")
    public String name() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
        // Need not be robust, so:
        RecordWithRename other = (RecordWithRename) o;
        return Objects.equals(this.id, other.id)
                && Objects.equals(this.name, other.name);
    }
  }

  static final class RecordWithHeaderInject extends RecordTag {
    private final int id;
    @JacksonInject
    private final String name;

    RecordWithHeaderInject(int id, @JacksonInject String name) {
      this.id = id;
      this.name = name;
    }

    public int id() {
      return id;
    }

    @JacksonInject
    public String name() {
      return name;
    }
  }

  static final class RecordWithJsonDeserialize extends RecordTag {
    private final int id;
    @JsonDeserialize(converter = StringTrimmer.class)
    private final String name;

    RecordWithJsonDeserialize(int id, @JsonDeserialize(converter = StringTrimmer.class) String name) {
      this.id = id;
      this.name = name;
    }

    public int id() {
      return id;
    }

    @JsonDeserialize(converter = StringTrimmer.class)
    public String name() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof RecordWithJsonDeserialize)) {
        return false;
      }
      RecordWithJsonDeserialize that = (RecordWithJsonDeserialize) o;
      return id == that.id && Objects.equals(name, that.name);
    }

    @Override
    public String toString() {
      return "RecordWithJsonDeserialize{" +
              "id=" + id +
              ", name='" + name + '\'' +
              '}';
    }
  }

  private final ObjectMapper MAPPER = newJsonMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    /*
    /**********************************************************************
    /* Test methods, renames, injects
    /**********************************************************************
     */

    // Fails in BasicDeserializerFactory._addImplicitConstructorCreators before reaching module logic:
    // Invalid type definition for type `com.fasterxml.jackson.module.androidrecord.RecordBasicsTest$RecordWithRename`: Argument #0 of constructor [constructor for `com.fasterxml.jackson.module.androidrecord.RecordBasicsTest$RecordWithRename` (2 args), annotations: [null] has no property name annotation; must have name when multiple-parameter constructor annotated as Creator
    public void testDeserializeJsonRename() throws Exception {
        RecordWithRename value = MAPPER.readValue("{\"id\":123,\"rename\":\"Bob\"}",
                RecordWithRename.class);
        assertEquals(new RecordWithRename(123, "Bob"), value);
    }

    // Fails by deserializing successfully, even though annotations on header are "propagated" to the field
    /**
     * This test-case is just for documentation purpose:
     * GOTCHA: Annotations on header will be propagated to the field, leading to this failure.
     *
     * @see #testDeserializeConstructorInjectRecord()
     */
    public void testDeserializeHeaderInjectRecord_WillFail() throws Exception {
        MAPPER.setInjectableValues(new InjectableValues.Std().addValue(String.class, "Bob"));

        try {
            MAPPER.readValue("{\"id\":123}", RecordWithHeaderInject.class);

            fail("should not pass");
        } catch (IllegalArgumentException e) {
            verifyException(e, "RecordWithHeaderInject#name");
            verifyException(e, "Can not set final java.lang.String field");
        }
    }

    /*
    /**********************************************************************
    /* Test methods, JsonDeserialize
    /**********************************************************************
     */

    // Fails: converter not applied
    public void testDeserializeJsonDeserializeRecord() throws Exception {
        RecordWithJsonDeserialize value = MAPPER.readValue("{\"id\":123,\"name\":\"   Bob   \"}",
                RecordWithJsonDeserialize.class);

        assertEquals(new RecordWithJsonDeserialize(123, "Bob"), value);
    }

  public static class StringTrimmer implements Converter<String, String> {

        @Override
        public String convert(String value) {
            return value.trim();
        }

        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return typeFactory.constructType(String.class);
        }

        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return typeFactory.constructType(String.class);
        }
    }
}
