module com.fasterxml.jackson.module.guice {
    //Sun/Oracle implementation
    requires static javax.inject;
    //Jakarta Reference Implementation
    requires static jakarta.inject;

    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    requires com.google.guice;

    exports com.fasterxml.jackson.module.guice;

    //NOTE : Don't auto provide jackson guice module as some may want to bind their own ObjectMapper?
}
