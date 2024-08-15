package tools.jackson.module.androidrecord.failing;

import com.android.tools.r8.RecordTag;

import com.fasterxml.jackson.annotation.JacksonInject;

import tools.jackson.databind.*;

import tools.jackson.module.androidrecord.BaseMapTest;
import tools.jackson.module.androidrecord.RecordBasicsTest;

public class RecordBasicsFailingTest extends BaseMapTest {
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

  private final ObjectMapper MAPPER = jsonMapperBuilder()
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .build();

  // Fails by deserializing successfully, even though annotations on header are "propagated" to the field

  /**
   * This test-case is just for documentation purpose:
   * GOTCHA: Annotations on header will be propagated to the field, leading to this failure.
   *
   * @see RecordBasicsTest#testDeserializeConstructorInjectRecord()
   */
  public void testDeserializeHeaderInjectRecord_WillFail() throws Exception {
      ObjectReader r = MAPPER.readerFor(RecordWithHeaderInject.class)
              .with(new InjectableValues.Std().addValue(String.class, "Bob"));
    try {
      r.readValue("{\"id\":123}");

      fail("should not pass");
    } catch (IllegalArgumentException e) {
      verifyException(e, "RecordWithHeaderInject#name");
      verifyException(e, "Can not set final java.lang.String field");
    }
  }
}
