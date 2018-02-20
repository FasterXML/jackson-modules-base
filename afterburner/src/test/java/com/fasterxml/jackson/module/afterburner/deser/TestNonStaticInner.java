package com.fasterxml.jackson.module.afterburner.deser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class TestNonStaticInner extends AfterburnerTestBase
{
    static class Parent {

        private Child child;

        public Child getChild() {
            return child;
        }

        public void setChild(Child child) {
            this.child = child;
        }

        public /* not static */ class Child {
            private boolean value;

            public boolean isValue() {
                return value;
            }

            public void setValue(boolean value) {
                this.value = value;
            }
        }
    }

    public void testInnerClass() throws  Exception {
        final ObjectMapper MAPPER = newAfterburnerMapper();
//        final ObjectMapper MAPPER = new ObjectMapper();

        Parent parent = MAPPER.readValue("{\"child\":{\"value\":true}}", Parent.class);

        assertTrue(parent.getChild().isValue());
    }
}
