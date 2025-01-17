// Jakarta XML Bind Main artifact Module descriptor
module tools.jackson.module.jakarta.xmlbind
{
    requires com.fasterxml.jackson.annotation;
    requires tools.jackson.core;
    requires transitive tools.jackson.databind;

    requires jakarta.xml.bind;
    requires static jakarta.activation;

    // expose main level, but leave out "ser", "deser" impl
    exports tools.jackson.module.jakarta.xmlbind;

    provides tools.jackson.databind.JacksonModule with
        tools.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
}
