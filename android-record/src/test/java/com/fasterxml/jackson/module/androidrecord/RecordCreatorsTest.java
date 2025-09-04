package com.fasterxml.jackson.module.androidrecord;

import org.junit.jupiter.api.Test;

import com.android.tools.r8.RecordTag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RecordCreatorsTest extends BaseMapTest
{
  static final class RecordWithCanonicalCtorOverride extends RecordTag {
    private final int id;
    private final String name;

    public int id() {
      return id;
    }

    public String name() {
      return name;
    }

    public RecordWithCanonicalCtorOverride(int id, String name) {
      this.id = id;
      this.name = "name";
    }
  }

  // [databind#2980]
  static final class RecordWithDelegation extends RecordTag {
    private final String value;

    public String value() {
      return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public RecordWithDelegation(String value) {
      this.value = "del:" + value;
    }

    @JsonValue()
    public String getValue() {
      return "val:" + value;
    }

    public String accessValueForTest() {
      return value;
    }
  }

    private final ObjectMapper MAPPER = newJsonMapper();

    /*
    /**********************************************************************
    /* Test methods, alternate constructors
    /**********************************************************************
     */

    @Test
    public void testDeserializeWithCanonicalCtorOverride() throws Exception {
        RecordWithCanonicalCtorOverride value = MAPPER.readValue("{\"id\":123,\"name\":\"Bob\"}",
                RecordWithCanonicalCtorOverride.class);
        assertEquals(123, value.id());
        assertEquals("name", value.name());
    }

    // [databind#2980]
    @Test
    public void testDeserializeWithDelegatingCtor() throws Exception {
        RecordWithDelegation value = MAPPER.readValue(q("foobar"),
                RecordWithDelegation.class);
        assertEquals("del:foobar", value.accessValueForTest());

        assertEquals(q("val:del:foobar"), MAPPER.writeValueAsString(value));
    }
}
