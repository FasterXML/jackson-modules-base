package tools.jackson.module.androidrecord;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.android.tools.r8.RecordTag;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Inner test classes simulate Android-desugared records.
 *
 * @author Eran Leshem
 **/
public class AndroidRecordTest
    extends BaseMapTest
{
  static final class Simple extends RecordTag {
    static int si = 7;
    private final int i;
    private final int j;
    private final String s;
    private final List<String> l;
    private final AtomicInteger ai;

    Simple(int i, int j, String s, List<String> l, AtomicInteger ai) {
      this.i = i;
      this.j = j;
      this.s = s;
      this.l = l;
      this.ai = ai;
    }

    int i() {
      return i;
    }

    String s() {
      return s;
    }

    List<String> l() {
      return l;
    }

    int j() {
      return j;
    }

    AtomicInteger ai() {
      return ai;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Simple)) {
        return false;
      }
      Simple simple = (Simple) o;
      return i == simple.i && j == simple.j && Objects.equals(s, simple.s) && Objects.equals(l, simple.l)
              && Objects.equals(ai.get(), simple.ai.get());
    }
  }

  static final class MultipleConstructors extends RecordTag {
    private final int i;
    private final List<String> l;

    MultipleConstructors(int i, List<String> l) {
      this.i = i;
      this.l = l;
    }

    MultipleConstructors(String s, List<String> l) {
      i = Integer.parseInt(s);
      this.l = l;
    }

    MultipleConstructors(int i, String s, List<String> l) {
      this.i = i;
      this.l = l;
    }

    MultipleConstructors(List<Integer> l, int i) {
      this.i = i;
      this.l = null;
    }

    int i() {
      return i;
    }

    List<String> l() {
      return l;
    }
  }

  static final class ConflictingConstructors extends RecordTag {
    private final int i;
    private final String s;

    ConflictingConstructors(int i, String s) {
      this.i = i;
      this.s = s;
    }

    ConflictingConstructors(String s, int i) {
      this.i = i;
      this.s = s;
    }

    public int i() {
      return i;
    }

    public String s() {
      return s;
    }
  }

  private final ObjectMapper _objectMapper = JsonMapper.builder()
          .addModule(new AndroidRecordModule())
          .changeDefaultVisibility(vc -> vc.withVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY))
          .build();

  @Test
  public void testSimple() throws Exception {
    Simple simple = new Simple(9, 3, "foo", Arrays.asList("bar", "baz"), new AtomicInteger(8));
    assertEquals(simple, _objectMapper.readValue(_objectMapper.writeValueAsString(simple), Simple.class));
  }

  @Test
  public void testMultipleConstructors() throws Exception {
    List<String> l = Arrays.asList("bar", "baz");
    assertEquals(9, _objectMapper.readValue(_objectMapper.writeValueAsString(new MultipleConstructors(9, l)),
                                        MultipleConstructors.class).i());
    assertEquals(9, _objectMapper.readValue(_objectMapper.writeValueAsString(
                    new MultipleConstructors("9", l)), MultipleConstructors.class).i());
    assertEquals(9, _objectMapper.readValue(_objectMapper.writeValueAsString(
                    new MultipleConstructors(9,"foobar", l)), MultipleConstructors.class).i());
    assertEquals(9, _objectMapper.readValue(_objectMapper.writeValueAsString(
                    new MultipleConstructors(Arrays.asList(1, 2), 9)), MultipleConstructors.class).i());
  }

  @Test
  public void testConflictingConstructors() {
    assertThrows(InvalidDefinitionException.class,
             () -> _objectMapper.readValue(_objectMapper.writeValueAsString(
                     new ConflictingConstructors(9, "foo")), ConflictingConstructors.class));
  }
}
