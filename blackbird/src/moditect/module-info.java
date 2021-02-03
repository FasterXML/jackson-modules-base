// Generated 14-Mar-2019 using Moditect maven plugin

module com.fasterxml.jackson.module.blackbird {
    requires java.logging;

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    exports com.fasterxml.jackson.module.blackbird;

    provides com.fasterxml.jackson.databind.JacksonModule with
        com.fasterxml.jackson.module.blackbird.BlackbirdModule;
}
