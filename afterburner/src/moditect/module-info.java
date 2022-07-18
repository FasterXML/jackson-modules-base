// Generated 14-Mar-2019 using Moditect maven plugin

module tools.jackson.module.afterburner {
    requires java.logging;

    requires tools.jackson.core;
    requires tools.jackson.databind;

    exports tools.jackson.module.afterburner;
// do not expose shaded ByteBuddy
//    exports tools.jackson.module.afterburner.bytebuddy;

// nor internal implementations of sub-packages
//    exports tools.jackson.module.afterburner.deser;
//    exports tools.jackson.module.afterburner.ser;
//    exports tools.jackson.module.afterburner.util;

    provides tools.jackson.databind.JacksonModule with
        tools.jackson.module.afterburner.AfterburnerModule;
}
