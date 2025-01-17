// Guice-module (unit) Test Module descriptor
module tools.jackson.module.guice
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies

    requires com.fasterxml.jackson.annotation;
    requires tools.jackson.core;
    requires tools.jackson.databind;

    requires com.google.guice;

    // Sun/Oracle implementation
    requires static javax.inject;
    // Jakarta Reference Implementation
    //requires static jakarta.inject;

    // Additional test lib/framework dependencies

    requires junit; // JUnit4 To Be Removed in future

    // Further, need to open up some packages for JUnit et al
    opens tools.jackson.module.guice;
}
