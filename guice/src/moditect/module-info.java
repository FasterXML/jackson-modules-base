module tools.jackson.module.guice {
    //Sun/Oracle implementation
    requires static javax.inject;
    //Jakarta Reference Implementation
    requires static jakarta.inject;

    requires com.fasterxml.jackson.annotation;

    requires tools.jackson.core;
    requires tools.jackson.databind;

    requires com.google.guice;

    exports tools.jackson.module.guice;

    //NOTE : Don't auto provide jackson guice module as some may want to bind their own ObjectMapper?
}
