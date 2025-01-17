// Jakarta XML Bind (unit) Test Module descriptor
module tools.jackson.module.jakarta.xmlbind
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies

    requires com.fasterxml.jackson.annotation;
    requires tools.jackson.core;
    requires tools.jackson.databind;

    requires jakarta.activation;
    requires jakarta.xml.bind;

    // Additional test lib/framework dependencies

    requires junit; // JUnit4 To Be Removed in future

    // Further, need to open up some packages for JUnit et al
    opens tools.jackson.module.jakarta.xmlbind;
    opens tools.jackson.module.jakarta.xmlbind.adapters;
    opens tools.jackson.module.jakarta.xmlbind.failing;
    opens tools.jackson.module.jakarta.xmlbind.id;
    opens tools.jackson.module.jakarta.xmlbind.introspect;
    opens tools.jackson.module.jakarta.xmlbind.misc;
    opens tools.jackson.module.jakarta.xmlbind.ser;
    opens tools.jackson.module.jakarta.xmlbind.types;
}
