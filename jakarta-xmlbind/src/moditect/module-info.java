module tools.jackson.module.jakarta.xmlbind
{
    requires static jakarta.xml.bind;

    requires static jakarta.activation;

    requires tools.jackson.core;
    requires tools.jackson.databind;

    // expose main level, but leave out "ser", "deser" impl
    exports tools.jackson.module.jakarta.xmlbind;

    provides tools.jackson.databind.JacksonModule with
        tools.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
}
