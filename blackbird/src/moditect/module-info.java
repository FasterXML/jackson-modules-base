// Generated 14-Mar-2019 using Moditect maven plugin

module com.fasterxml.jackson.module.afterburner {
    requires java.logging;

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    exports com.fasterxml.jackson.module.blackbird;
    exports com.fasterxml.jackson.module.blackbird.deser;
    exports com.fasterxml.jackson.module.blackbird.ser;

    provides com.fasterxml.jackson.databind.Module with
        com.fasterxml.jackson.module.blackbird.BlackbirdModule;
}
