// Blackbird (unit) Test Module descriptor
module tools.jackson.module.blackbird
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

    // Further, need to open up some packages for JUnit et al
    opens tools.jackson.module.blackbird;
    opens tools.jackson.module.blackbird.codegen;
    opens tools.jackson.module.blackbird.deser;
    opens tools.jackson.module.blackbird.deser.convert;
    opens tools.jackson.module.blackbird.deser.filter;
    opens tools.jackson.module.blackbird.deser.inject;
    opens tools.jackson.module.blackbird.deser.java8;
    opens tools.jackson.module.blackbird.deser.jdk;
    opens tools.jackson.module.blackbird.deser.merge;
    opens tools.jackson.module.blackbird.deser.struct;
    opens tools.jackson.module.blackbird.failing;
    opens tools.jackson.module.blackbird.format;
    opens tools.jackson.module.blackbird.misc;
    opens tools.jackson.module.blackbird.objectid;
    opens tools.jackson.module.blackbird.roundtrip;
    opens tools.jackson.module.blackbird.ser;
    opens tools.jackson.module.blackbird.ser.filter;
    opens tools.jackson.module.blackbird.testutil;
}
