// JAXB Annotations artifact Module descriptor
module tools.jackson.module.jaxb {
    requires com.fasterxml.jackson.annotation;
    requires tools.jackson.core;
    requires transitive tools.jackson.databind;

    requires static java.xml.bind;
    // Needed for JDK9+, but optionally only
    //requires static java.activation;

    // expose main level, but leave out "ser", "deser" impl
    exports tools.jackson.module.jaxb;

    provides tools.jackson.databind.JacksonModule with
        tools.jackson.module.jaxb.JaxbAnnotationModule;
}
