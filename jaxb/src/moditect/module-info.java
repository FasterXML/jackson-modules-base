module com.fasterxml.jackson.module.jaxb {
    requires java.logging;
    requires java.xml;
    requires java.xml.bind;
    //
    // This is for `BeanIntrospector`... should do away with (only need "Introspector.decapitalize")
    requires java.desktop;
    // Needed for JDK9+, but optionally only
    requires static java.activation;
    // Jakarta Release
    requires static jakarta.activation;

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    // expose main level, but leave out "ser", "deser" impl
    exports com.fasterxml.jackson.module.jaxb;

    provides com.fasterxml.jackson.databind.Module with
        com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
}
