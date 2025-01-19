// OSGi module (unit) Test Module descriptor
module tools.jackson.module.osgi
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies

    requires tools.jackson.core;
    requires tools.jackson.databind;
    requires org.osgi.core;

    // Additional test lib/framework dependencies

    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    // Further, need to open up some packages for JUnit et al
    opens tools.jackson.module.osgi;
}
