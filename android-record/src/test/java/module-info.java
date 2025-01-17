// Android-Record (unit) Test Module descriptor
module tools.jackson.module.androidrecord
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies

    requires com.fasterxml.jackson.annotation;
    requires tools.jackson.core;
    requires tools.jackson.databind;

    // Additional test lib/framework dependencies

    requires junit; // JUnit4 To Be Removed in future

    // Further, need to open up some packages for JUnit et al

    opens tools.jackson.module.androidrecord;
    opens tools.jackson.module.androidrecord.failing;
}
