module tools.jackson.module.blackbird {
    requires java.logging;

    requires tools.jackson.core;
    requires tools.jackson.databind;

    exports tools.jackson.module.blackbird;

    provides tools.jackson.databind.JacksonModule with
        tools.jackson.module.blackbird.BlackbirdModule;
}
