// Afterburner (unit) Test Module descriptor
module tools.jackson.module.afterburner
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies

    requires java.logging;

    requires com.fasterxml.jackson.annotation;
    requires tools.jackson.core;
    requires tools.jackson.databind;

    // Additional test lib/framework dependencies

    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    // Other test dependencies
    requires java.xml;
    requires java.desktop; // java.beans.ConstructorProperties
    requires net.bytebuddy;
    requires org.objectweb.asm;

    // Further, need to open up some packages for JUnit et al
    opens tools.jackson.module.afterburner;
    opens tools.jackson.module.afterburner.codegen;
    opens tools.jackson.module.afterburner.deser;
    opens tools.jackson.module.afterburner.deser.convert;
    opens tools.jackson.module.afterburner.deser.ctor;
    opens tools.jackson.module.afterburner.deser.filter;
    opens tools.jackson.module.afterburner.deser.inject;
    opens tools.jackson.module.afterburner.deser.java8;
    opens tools.jackson.module.afterburner.deser.jdk;
    opens tools.jackson.module.afterburner.deser.merge;
    opens tools.jackson.module.afterburner.deser.struct;
    opens tools.jackson.module.afterburner.format;
    opens tools.jackson.module.afterburner.misc;
    opens tools.jackson.module.afterburner.objectid;
    opens tools.jackson.module.afterburner.roundtrip;
    opens tools.jackson.module.afterburner.ser;
    opens tools.jackson.module.afterburner.ser.filter;
    opens tools.jackson.module.afterburner.testutil;
    opens tools.jackson.module.afterburner.util;
}
