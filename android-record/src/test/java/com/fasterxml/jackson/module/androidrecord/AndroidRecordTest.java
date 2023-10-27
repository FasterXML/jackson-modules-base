package com.fasterxml.jackson.module.androidrecord;

import com.android.tools.r8.RecordTag;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import junit.framework.TestCase;
import org.junit.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Inner test classes simulate Android-desugared records.
 *
 * @author Eran Leshem
 **/
public class AndroidRecordTest extends TestCase {
  private static final class Simple extends RecordTag {
    static int si = 7;
    private final int i;
    private final int j;
    private final String s;
    private final List<String> l;
    private final AtomicInteger ai;

    private Simple(int i, int j, String s, List<String> l, AtomicInteger ai) {
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

  private static final class MultipleConstructors extends RecordTag {
    private final int i;

    private MultipleConstructors(int i) {
      this.i = i;
    }

    private MultipleConstructors(String s) {
      i = Integer.parseInt(s);
    }


    private MultipleConstructors(int i, String s) {
      this.i = i;
    }

    int i() {
      return i;
    }
  }


  private static final class ConflictingConstructors extends RecordTag {
    private final int i;
    private final String s;

    private ConflictingConstructors(int i, String s) {
      this.i = i;
      this.s = s;
    }

    private ConflictingConstructors(String s, int i) {
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
                  .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                  .addModule(new AndroidRecordModule()).build();

  public void testSimple() throws JsonProcessingException {
    Simple simple = new Simple(9, 3, "foo", Arrays.asList("bar", "baz"), new AtomicInteger(8));
    assertEquals(simple, _objectMapper.readValue(_objectMapper.writeValueAsString(simple), Simple.class));
  }

  public void testMultipleConstructors() throws JsonProcessingException {
    assertEquals(9, _objectMapper.readValue(_objectMapper.writeValueAsString(new MultipleConstructors(9)),
                                        MultipleConstructors.class).i());
    assertEquals(9, _objectMapper.readValue(_objectMapper.writeValueAsString(
                    new MultipleConstructors("9")), MultipleConstructors.class).i());
    assertEquals(9, _objectMapper.readValue(_objectMapper.writeValueAsString(
                    new MultipleConstructors(9,"foobar")), MultipleConstructors.class).i());
  }

  public void testConflictingConstructors() {
    Assert.assertThrows(InvalidDefinitionException.class,
             () -> _objectMapper.readValue(_objectMapper.writeValueAsString(
                     new ConflictingConstructors(9, "foo")), ConflictingConstructors.class));
  }
}
