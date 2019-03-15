// Manually composed on 14-Mar-2019 -- Moditect maven plugin failed to generate

module com.fasterxml.jackson.module.jaxb {
    requires java.logging;
    requires javax.xml.bind;
    // This is for `BeanIntrospector`... should do away with
    requires static java.desktop;

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    // expose main level, but leave out "ser", "deser" impl
    exports com.fasterxml.jackson.module.jaxb;

    provides com.fasterxml.jackson.databind.Module with
        com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
}
