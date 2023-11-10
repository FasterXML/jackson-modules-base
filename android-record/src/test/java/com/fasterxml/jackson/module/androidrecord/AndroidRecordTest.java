package com.fasterxml.jackson.module.androidrecord;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.android.tools.r8.RecordTag;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.json.JsonMapper;
import junit.framework.TestCase;
import tools.jackson.module.androidrecord.AndroidRecordModule;

import org.junit.Assert;

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
    private final List<String> l;

    private MultipleConstructors(int i, List<String> l) {
      this.i = i;
      this.l = l;
    }

    private MultipleConstructors(String s, List<String> l) {
      i = Integer.parseInt(s);
      this.l = l;
    }

    private MultipleConstructors(int i, String s, List<String> l) {
      this.i = i;
      this.l = l;
    }

    private MultipleConstructors(List<Integer> l, int i) {
      this.i = i;
      this.l = null;
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

  public void testSimple() throws Exception {
    Simple simple = new Simple(9, 3, "foo", Arrays.asList("bar", "baz"), new AtomicInteger(8));
    assertEquals(simple, _objectMapper.readValue(_objectMapper.writeValueAsString(simple), Simple.class));
  }

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

  public void testConflictingConstructors() {
    Assert.assertThrows(InvalidDefinitionException.class,
             () -> _objectMapper.readValue(_objectMapper.writeValueAsString(
                     new ConflictingConstructors(9, "foo")), ConflictingConstructors.class));
  }
}
