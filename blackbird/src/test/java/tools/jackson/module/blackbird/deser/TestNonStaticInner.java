package tools.jackson.module.blackbird.deser;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

public class TestNonStaticInner extends BlackbirdTestBase
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
        final ObjectMapper MAPPER = newObjectMapper();
//        final ObjectMapper MAPPER = new ObjectMapper();

        Parent parent = MAPPER.readValue("{\"child\":{\"value\":true}}", Parent.class);

        assertTrue(parent.getChild().isValue());
    }
}
