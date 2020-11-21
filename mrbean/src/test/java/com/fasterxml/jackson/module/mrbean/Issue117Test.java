package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class Issue117Test extends BaseTest
{
    static class Model {
        @JsonSerialize(using = ToStringSerializer.class)
        private int primitiveValue;

        @JsonSerialize(using = ToStringSerializer.class)
        private Integer value;

        public int getPrimitiveValue() {
            return primitiveValue;
        }

        public Model setPrimitiveValue(int primitiveValue) {
            this.primitiveValue = primitiveValue;
            return this;
        }

        public Integer getValue() {
            return value;
        }

        public Model setValue(Integer value) {
            this.value = value;
            return this;
        }
    }


    public static class AnotherModel extends Model { }

    private final ObjectMapper MAPPER = newMrBeanMapper();

    static Model model = new Model().setPrimitiveValue(10).setValue(10);

//    static Model anotherModel = new AnotherModel().setPrimitiveValue(10).setValue(10);

    public void testIssue117() throws Exception
    {
        // will print: {"primitiveValue":"10","value":"10"}
        System.out.println(MAPPER.writeValueAsString(model));

        // will print: {"primitiveValue":10,"value":"10"}
        // primitiveValue lost JsonSerialize feature
        Model anotherModel = new AnotherModel().setPrimitiveValue(10).setValue(10);
        System.out.println(MAPPER.writeValueAsString(anotherModel));
    }
}
