// Generated 14-Mar-2019 using Moditect maven plugin

module com.fasterxml.jackson.module.afterburner {
    requires java.logging;

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    exports com.fasterxml.jackson.module.afterburner;
// do not expose shaded ByteBuddy
//    exports com.fasterxml.jackson.module.afterburner.bytebuddy;
    exports com.fasterxml.jackson.module.afterburner.deser;
    exports com.fasterxml.jackson.module.afterburner.ser;
    exports com.fasterxml.jackson.module.afterburner.util;

    provides com.fasterxml.jackson.databind.Module with
        com.fasterxml.jackson.module.afterburner.AfterburnerModule;

}
