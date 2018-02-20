package com.fasterxml.jackson.module.afterburner.codegen;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

// for [afterburner#51], where re-generation of classes does not work
// as expected
public class GenerateWithMixinsTest extends AfterburnerTestBase
{
    @JsonPropertyOrder({ "field1", "field2" })
    static class SampleObject {
        private String field1;
        private int field2;
        private byte[] field3;

        public SampleObject(String field1, int field2, byte[] field3) {
          this.field1 = field1;
          this.field2 = field2;
          this.field3 = field3;
        }

        public String getField1() {
          return field1;
        }

        public void setField1(String field1) {
          this.field1 = field1;
        }

        public int getField2() {
          return field2;
        }

        public void setField2(int field2) {
          this.field2 = field2;
        }

        public byte[] getField3() {
          return field3;
        }

        public void setField3(byte[] field3) {
          this.field3 = field3;
        }
      }

      public abstract class IgnoreField3MixIn {
          @JsonIgnore
          public abstract byte[] getField3();
      }

      public void testIssue51() throws Exception
      {
          _testIssue51(afterburnerMapperBuilder());

          // and, importantly, try again

          _testIssue51(afterburnerMapperBuilder());
      }

      public void _testIssue51(MapperBuilder<?,?> mapperB) throws Exception
      {
          SampleObject sampleObject = new SampleObject("field1", 2, "field3".getBytes());
          ObjectMapper mapper = mapperB
                  .addMixIn(SampleObject.class, IgnoreField3MixIn.class)
                  .build();
          String json = mapper.writeValueAsString(sampleObject);
          assertEquals(aposToQuotes("{'field1':'field1','field2':2}"), json);
      }
}
