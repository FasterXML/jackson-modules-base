// JAXB Annotations (unit) Test Module descriptor
module tools.jackson.module.jaxb
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies

    requires com.fasterxml.jackson.annotation;
    requires tools.jackson.core;
    requires tools.jackson.databind;

    requires static java.xml.bind;
    // Needed for JDK9+, but optionally only
    //requires static java.activation;

    // Additional test lib/framework dependencies

    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    // Further, need to open up some packages for JUnit et al

    opens tools.jackson.module.jaxb;
    opens tools.jackson.module.jaxb.adapters;
    opens tools.jackson.module.jaxb.id;
    opens tools.jackson.module.jaxb.introspect;
    opens tools.jackson.module.jaxb.misc;
    opens tools.jackson.module.jaxb.ser;
    opens tools.jackson.module.jaxb.testutil.failure;
    opens tools.jackson.module.jaxb.tofix;
    opens tools.jackson.module.jaxb.types;
}
