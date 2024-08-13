package com.fasterxml.jackson.module.androidrecord;

import com.android.tools.r8.RecordTag;
import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;
import org.junit.Assert;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class RecordBasicsTest extends BaseMapTest {
  static final class EmptyRecord extends RecordTag {
    @Override
    public boolean equals(Object o) {
      return o instanceof EmptyRecord;
    }

  }

  static final class SimpleRecord extends RecordTag {
    private final int id;
    private final String name;

    SimpleRecord(int id, String name) {
      this.id = id;
      this.name = name;
    }

    public int id() {
      return id;
    }

    public String name() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SimpleRecord)) {
        return false;
      }
      SimpleRecord that = (SimpleRecord) o;
      return id == that.id && Objects.equals(name, that.name);
    }
  }

  static final class RecordOfRecord extends RecordTag {
    private final SimpleRecord record;

    RecordOfRecord(SimpleRecord record) {
      this.record = record;
    }

    public SimpleRecord record() {
      return record;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof RecordOfRecord)) {
        return false;
      }
      RecordOfRecord that = (RecordOfRecord) o;
      return Objects.equals(record, that.record);
    }
  }

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

  static final class RecordWithConstructorInject extends RecordTag {
    private final int id;
    private final String name;

    RecordWithConstructorInject(int id, @JacksonInject String name) {
      this.id = id;
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof RecordWithConstructorInject)) return false;
      RecordWithConstructorInject that = (RecordWithConstructorInject) o;
      return id == that.id && Objects.equals(name, that.name);
    }

    @Override
    public String toString() {
      return "RecordWithConstructorInject{" +
              "id=" + id +
              ", name='" + name + '\'' +
              '}';
    }
  }

  // [databind#2992]
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  static final class SnakeRecord extends RecordTag {
    private final String myId;
    private final String myValue;

    SnakeRecord(String myId, String myValue) {
      this.myId = myId;
      this.myValue = myValue;
    }

    public String myId() {
      return myId;
    }

    public String myValue() {
      return myValue;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SnakeRecord)) {
        return false;
      }
      SnakeRecord that = (SnakeRecord) o;
      return Objects.equals(myId, that.myId) && Objects.equals(myValue, that.myValue);
    }
  }

  static final class RecordSingleWriteOnly extends RecordTag {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private final int id;

    RecordSingleWriteOnly(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) int id) {
      this.id = id;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public int id() {
      return id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof RecordSingleWriteOnly)) {
        return false;
      }
      RecordSingleWriteOnly that = (RecordSingleWriteOnly) o;
      return id == that.id;
    }
  }

  static final class RecordSomeWriteOnly extends RecordTag {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private final int id;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private final String name;
    private final String email;

    RecordSomeWriteOnly(
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) int id,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String name, String email) {
      this.id = id;
      this.name = name;
      this.email = email;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public int id() {
      return id;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String name() {
      return name;
    }

    public String email() {
      return email;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof RecordSomeWriteOnly)) {
        return false;
      }
      RecordSomeWriteOnly that = (RecordSomeWriteOnly) o;
      return id == that.id && Objects.equals(name, that.name) && Objects.equals(email, that.email);
    }
  }

  static final class RecordAllWriteOnly extends RecordTag {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private final int id;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private final String name;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private final String email;

    RecordAllWriteOnly(
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) int id,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String name,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String email) {
      this.id = id;
      this.name = name;
      this.email = email;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public int id() {
      return id;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String name() {
      return name;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String email() {
      return email;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof RecordAllWriteOnly)) {
        return false;
      }
      RecordAllWriteOnly that = (RecordAllWriteOnly) o;
      return id == that.id && Objects.equals(name, that.name) && Objects.equals(email, that.email);
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
  /* Test methods, Record type introspection
  /**********************************************************************
  */

  public void testClassUtil() {
    assertFalse(AndroidRecordModule.isDesugaredRecordClass(getClass()));
    assertTrue(AndroidRecordModule.isDesugaredRecordClass(SimpleRecord.class));
    assertTrue(AndroidRecordModule.isDesugaredRecordClass(RecordOfRecord.class));
    assertTrue(AndroidRecordModule.isDesugaredRecordClass(RecordWithRename.class));
  }

  public void testRecordJavaType() {
    assertFalse(AndroidRecordModule.isDesugaredRecordClass(MAPPER.constructType(getClass()).getRawClass()));
    assertTrue(AndroidRecordModule.isDesugaredRecordClass(MAPPER.constructType(SimpleRecord.class).getRawClass()));
    assertTrue(AndroidRecordModule.isDesugaredRecordClass(MAPPER.constructType(RecordOfRecord.class).getRawClass()));
    assertTrue(AndroidRecordModule.isDesugaredRecordClass(MAPPER.constructType(RecordWithRename.class).getRawClass()));
  }

  /*
  /**********************************************************************
  /* Test methods, default reading/writing Record values
  /**********************************************************************
   */

  public void testSerializeSimpleRecord() throws Exception {
    String json = MAPPER.writeValueAsString(new SimpleRecord(123, "Bob"));
    final Object EXP = map("id", Integer.valueOf(123), "name", "Bob");
    assertEquals(EXP, MAPPER.readValue(json, Object.class));
  }

  public void testDeserializeSimpleRecord() throws Exception {
    assertEquals(new SimpleRecord(123, "Bob"),
            MAPPER.readValue("{\"id\":123,\"name\":\"Bob\"}", SimpleRecord.class));
  }

  public void testSerializeEmptyRecord() throws Exception {
    assertEquals("{}", MAPPER.writeValueAsString(new EmptyRecord()));
  }

  public void testDeserializeEmptyRecord() throws Exception {
    assertEquals(new EmptyRecord(),
            MAPPER.readValue("{}", EmptyRecord.class));
  }

  public void testSerializeRecordOfRecord() throws Exception {
    RecordOfRecord record = new RecordOfRecord(new SimpleRecord(123, "Bob"));
    String json = MAPPER.writeValueAsString(record);
    final Object EXP = Collections.singletonMap("record",
            map("id", Integer.valueOf(123), "name", "Bob"));
    assertEquals(EXP, MAPPER.readValue(json, Object.class));
  }

  public void testDeserializeRecordOfRecord() throws Exception {
    assertEquals(new RecordOfRecord(new SimpleRecord(123, "Bob")),
            MAPPER.readValue("{\"record\":{\"id\":123,\"name\":\"Bob\"}}",
                    RecordOfRecord.class));
  }

  /*
  /**********************************************************************
  /* Test methods, reading/writing Record values with different config
  /**********************************************************************
   */

  public void testSerializeSimpleRecord_DisableAnnotationIntrospector() throws Exception {
    SimpleRecord record = new SimpleRecord(123, "Bob");

    JsonMapper mapper = JsonMapper.builder().addModule(new AndroidRecordModule())
            .configure(MapperFeature.USE_ANNOTATIONS, false)
            .build();
    String json = mapper.writeValueAsString(record);

    assertEquals("{\"id\":123,\"name\":\"Bob\"}", json);
  }

  public void testDeserializeSimpleRecord_DisableAnnotationIntrospector() throws Exception {
    JsonMapper mapper = JsonMapper.builder().addModule(new AndroidRecordModule())
            .configure(MapperFeature.USE_ANNOTATIONS, false)
            .build();

    Assert.assertThrows(InvalidDefinitionException.class, () -> mapper.readValue("{\"id\":123,\"name\":\"Bob\"}", SimpleRecord.class));
  }

  /*
  /**********************************************************************
  /* Test methods, renames, injects
  /**********************************************************************
   */

  public void testSerializeJsonRename() throws Exception {
    String json = MAPPER.writeValueAsString(new RecordWithRename(123, "Bob"));
    final Object EXP = map("id", Integer.valueOf(123), "rename", "Bob");
    assertEquals(EXP, MAPPER.readValue(json, Object.class));
  }

  public void testDeserializeJsonRename() throws Exception {
    RecordWithRename value = MAPPER.readValue("{\"id\":123,\"rename\":\"Bob\"}",
            RecordWithRename.class);
    assertEquals(new RecordWithRename(123, "Bob"), value);
  }

  public void testDeserializeConstructorInjectRecord() throws Exception {
    MAPPER.setInjectableValues(new InjectableValues.Std().addValue(String.class, "Bob"));

    RecordWithConstructorInject value = MAPPER.readValue("{\"id\":123}", RecordWithConstructorInject.class);
    assertEquals(new RecordWithConstructorInject(123, "Bob"), value);
  }

  /*
  /**********************************************************************
  /* Test methods, naming strategy
  /**********************************************************************
   */

  // [databind#2992]
  public void testNamingStrategy() throws Exception {
    SnakeRecord input = new SnakeRecord("123", "value");

    String json = MAPPER.writeValueAsString(input);
    assertEquals("{\"my_id\":\"123\",\"my_value\":\"value\"}", json);

    SnakeRecord output = MAPPER.readValue(json, SnakeRecord.class);
    assertEquals(input, output);
  }

  /*
  /**********************************************************************
  /* Test methods, JsonProperty(access=WRITE_ONLY)
  /**********************************************************************
   */

  public void testSerialize_SingleWriteOnlyParameter() throws Exception {
    String json = MAPPER.writeValueAsString(new RecordSingleWriteOnly(123));

    assertEquals("{}", json);
  }

  // [databind#3897]
  public void testDeserialize_SingleWriteOnlyParameter() throws Exception {
    RecordSingleWriteOnly value = MAPPER.readValue("{\"id\":123}", RecordSingleWriteOnly.class);

    assertEquals(new RecordSingleWriteOnly(123), value);
  }

  public void testSerialize_SomeWriteOnlyParameter() throws Exception {
    String json = MAPPER.writeValueAsString(new RecordSomeWriteOnly(123, "Bob", "bob@example.com"));

    assertEquals("{\"email\":\"bob@example.com\"}", json);
  }

  public void testDeserialize_SomeWriteOnlyParameter() throws Exception {
    RecordSomeWriteOnly value = MAPPER.readValue(
            "{\"id\":123,\"name\":\"Bob\",\"email\":\"bob@example.com\"}",
            RecordSomeWriteOnly.class);

    assertEquals(new RecordSomeWriteOnly(123, "Bob", "bob@example.com"), value);
  }

  public void testSerialize_AllWriteOnlyParameter() throws Exception {
    String json = MAPPER.writeValueAsString(new RecordAllWriteOnly(123, "Bob", "bob@example.com"));

    assertEquals("{}", json);
  }

  public void testDeserialize_AllWriteOnlyParameter() throws Exception {
    RecordAllWriteOnly value = MAPPER.readValue(
            "{\"id\":123,\"name\":\"Bob\",\"email\":\"bob@example.com\"}",
            RecordAllWriteOnly.class);

    assertEquals(new RecordAllWriteOnly(123, "Bob", "bob@example.com"), value);
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

  /*
  /**********************************************************************
  /* Internal helper methods
  /**********************************************************************
   */

  private static Map<String, Object> map(String key1, Object value1,
                                         String key2, Object value2) {
    final Map<String, Object> result = new LinkedHashMap<>();
    result.put(key1, value1);
    result.put(key2, value2);
    return result;
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