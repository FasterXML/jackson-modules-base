package tools.jackson.module.androidrecord;

import org.junit.jupiter.api.Test;

import com.android.tools.r8.RecordTag;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AbstractRecordMemberTest extends BaseMapTest
{
  static final class RootRecord extends RecordTag
  {
    private final AbstractMember member;

    public RootRecord(AbstractMember member) {
      this.member = member;
    }

    public AbstractMember member() {
      return member;
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@class")
  @JsonSubTypes({
          @JsonSubTypes.Type(value = StringMember.class, name = "string"),
          @JsonSubTypes.Type(value = IntMember.class, name = "int")
  })
  static abstract class AbstractMember {
  }

  static final class StringMember extends AbstractMember {

    private final String val;

    @JsonCreator
    public StringMember(@JsonProperty("val") String val) {
      this.val = val;
    }

    public String getVal() {
      return val;
    }
  }

  static final class IntMember extends AbstractMember {

    private final int val;

    @JsonCreator
    public IntMember(@JsonProperty("val") int val) {
      this.val = val;
    }

    public int getVal() {
      return val;
    }
  }

  private final ObjectMapper MAPPER = newJsonMapper();

  /*
  /**********************************************************************
  /* https://github.com/FasterXML/jackson-modules-base/issues/248
  /**********************************************************************
   */

  @Test
  public void testDeserializeRecordWithAbstractMember() throws Exception {
    RootRecord value = MAPPER.readValue("{\"member\":{\"@class\":\"string\",\"val\":\"Hello, abstract member!\"}}",
            RootRecord.class);
    assertNotNull(value.member());
    assertEquals(StringMember.class, value.member().getClass());
    assertEquals("Hello, abstract member!", ((StringMember)value.member()).getVal());
  }
}
