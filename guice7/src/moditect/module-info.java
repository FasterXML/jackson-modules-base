module com.fasterxml.jackson.module.guice7 {
    //Jakarta Reference Implementation
    requires static jakarta.inject;

    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    requires com.google.guice;

    exports com.fasterxml.jackson.module.guice7;

    //NOTE : Don't auto provide jackson guice module as some may want to bind their own ObjectMapper?
}
