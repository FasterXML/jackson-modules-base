// Guice7-module (unit) Test Module descriptor
module tools.jackson.module.guice7
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies

    requires com.fasterxml.jackson.annotation;
    requires tools.jackson.core;
    requires tools.jackson.databind;

    requires com.google.guice;

    //Jakarta Reference Implementation
    requires static jakarta.inject;

    // Additional test lib/framework dependencies

    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    // Further, need to open up some packages for JUnit et al
    opens tools.jackson.module.guice7;
}
