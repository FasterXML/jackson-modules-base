module tools.jackson.module.jaxb {
    requires static java.xml.bind;

    // Needed for JDK9+, but optionally only
    requires static java.activation;

    requires tools.jackson.core;
    requires tools.jackson.databind;

    // expose main level, but leave out "ser", "deser" impl
    exports tools.jackson.module.jaxb;

    provides tools.jackson.databind.JacksonModule with
        tools.jackson.module.jaxb.JaxbAnnotationModule;
}
