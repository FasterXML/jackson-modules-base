module com.fasterxml.jackson.module.jakarta.xmlbind {
    requires static jakarta.xml.bind;

    requires static jakarta.activation;

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    // expose main level, but leave out "ser", "deser" impl
    exports com.fasterxml.jackson.module.jakarta.xmlbind;

    provides com.fasterxml.jackson.databind.JacksonModule with
        com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
}
