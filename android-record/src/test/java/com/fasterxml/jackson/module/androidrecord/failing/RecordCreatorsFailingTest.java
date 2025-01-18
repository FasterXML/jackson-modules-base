package com.fasterxml.jackson.module.androidrecord.failing;

import org.junit.jupiter.api.Test;

import com.android.tools.r8.RecordTag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import com.fasterxml.jackson.module.androidrecord.BaseMapTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class RecordCreatorsFailingTest extends BaseMapTest
{
  static final class RecordWithAltCtor extends RecordTag
  {
    private final int id;
    private final String name;

    RecordWithAltCtor(int id, String name) {
      this.id = id;
      this.name = name;
    }

    public int id() {
      return id;
    }

    public String name() {
      return name;
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RecordWithAltCtor(@JsonProperty("id") int id) {
      this(id, "name2");
    }
  }

  private final ObjectMapper MAPPER = newJsonMapper();

  /*
  /**********************************************************************
  /* Test methods, alternate constructors
  /**********************************************************************
  */

  // Fails: Implicit canonical constructor still works too
  @Test
  public void testDeserializeWithAltCtor() throws Exception {
    RecordWithAltCtor value = MAPPER.readValue("{\"id\":2812}",
            RecordWithAltCtor.class);
    assertEquals(2812, value.id());
    assertEquals("name2", value.name());

    // "Implicit" canonical constructor can no longer be used when there's explicit constructor
    try {
      MAPPER.readValue("{\"id\":2812,\"name\":\"Bob\"}",
              RecordWithAltCtor.class);
      fail("should not pass");
    } catch (UnrecognizedPropertyException e) {
      verifyException(e, "Unrecognized");
      verifyException(e, "\"name\"");
      verifyException(e, "RecordWithAltCtor");
    }
  }
}
