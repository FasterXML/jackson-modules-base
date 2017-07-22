package com.fasterxml.jackson.module.jaxb.types;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

abstract class PolymorpicTestBase extends BaseJaxbTest
{
    static abstract class Animal {
        public String nickname;

        protected Animal(String n) { nickname = n; }
    }

    static class Buffalo extends Animal {
        public String hairColor;

        public Buffalo() { this(null, null); }
        public Buffalo(String name, String hc) {
            super(name);
            hairColor = hc;
        }
    }

    static class Whale extends Animal {
        public int weightInTons;
        public Whale() { this(null, 0); }
        public Whale(String n, int w) {
            super(n);
            weightInTons = w;
        }
    }

    @XmlRootElement
    static class Emu extends Animal {
        public String featherColor;
        public Emu() { this(null, null); }
        public Emu(String n, String w) {
            super(n);
            featherColor = w;
        }
    }

    @XmlRootElement (name="moo")
    static class Cow extends Animal {
        public int weightInPounds;
        public Cow() { this(null, 0); }
        public Cow(String n, int w) {
            super(n);
            weightInPounds = w;
        }
    }

}
