// Mr Bean (unit) Test Module descriptor
module tools.jackson.module.mrbean
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies

    requires com.fasterxml.jackson.annotation;
    requires tools.jackson.core;
    requires tools.jackson.databind;

    requires static net.bytebuddy;

    // Additional test lib/framework dependencies

    requires junit; // JUnit4 To Be Removed in future

    // Other test dependencies
    requires org.objectweb.asm;

    // Further, need to open up some packages for JUnit et al
    opens tools.jackson.module.mrbean;
}
