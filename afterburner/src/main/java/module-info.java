// Afterburner Main artifact Module descriptor
module tools.jackson.module.afterburner
{
    requires java.logging;

    requires tools.jackson.core;
    requires transitive tools.jackson.databind;

    exports tools.jackson.module.afterburner;
    // do not expose internal implementations of sub-packages
    //    exports tools.jackson.module.afterburner.deser;
    //    exports tools.jackson.module.afterburner.ser;
    //    exports tools.jackson.module.afterburner.util;

    // do not expose shaded ByteBuddy
    //    exports tools.jackson.module.afterburner.bytebuddy;
    // but need to depend on it?
    requires static net.bytebuddy;

    provides tools.jackson.databind.JacksonModule with
        tools.jackson.module.afterburner.AfterburnerModule;
}
