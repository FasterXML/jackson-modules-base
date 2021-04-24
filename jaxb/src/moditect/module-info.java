module com.fasterxml.jackson.module.jaxb {
    requires static java.xml.bind;
    requires static jakarta.xml.bind;

    // Needed for JDK9+, but optionally only
    requires static java.activation;
    // Jakarta Release
    requires static jakarta.activation;

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    // expose main level, but leave out "ser", "deser" impl
    exports com.fasterxml.jackson.module.jaxb;

    provides com.fasterxml.jackson.databind.JacksonModule with
        com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
}
