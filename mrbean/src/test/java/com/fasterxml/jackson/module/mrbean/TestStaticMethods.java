package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test(s) to ensure that `static` methods are not considered to be
 * accessors.
 * Specifically, test for [module-mrbean#25]
 */
public class TestStaticMethods extends BaseTest
{
    static class TestPOJO
    {
        public AbstractRoot root;

        public void setRoot(AbstractRoot root) {
            this.root = root;
        }

        public AbstractRoot getRoot() {
            return root;
        }
    }

    public static class Root extends AbstractRoot {
        public Root() {}
    }

    public static abstract class AbstractRoot
    {
        static Leaf leaf;

        private int x;

        public AbstractRoot() {}

        // bogus: being static, ought to be skipped
        public static void setLeaf(Leaf leaf) {
            AbstractRoot.leaf = leaf;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }
    }

    //Bad jackson class, to generate the IllegalArgumentException
    //This class should be ignored by Jackson
    static class Leaf
    {
        public String test;

        public void setTest(String test) {
            this.test = test;
        }

        public void setTest(StringBuilder test) {
            this.test = test.toString();
        }
    }

    public void testAbstract25() throws Exception
    {
        TestPOJO v = new TestPOJO();

        Root root = new Root();
        root.setX(2);

        v.setRoot(root);

        ObjectMapper mapper = newMrBeanMapper();
        String jsonValue = mapper.writeValueAsString(v);

        TestPOJO result = mapper.readValue(jsonValue, TestPOJO.class);
        assertNotNull(result);
    }
}
