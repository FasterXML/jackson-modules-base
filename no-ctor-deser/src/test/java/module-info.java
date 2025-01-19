// No-Constructor module (unit) Test Module descriptor
module tools.jackson.module.noctordeser
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies

    requires tools.jackson.core;
    requires transitive tools.jackson.databind;

    requires jdk.unsupported; 

    // Additional test lib/framework dependencies

    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    // Further, need to open up some packages for JUnit et al
    opens tools.jackson.module.noctordeser;
}
