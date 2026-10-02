// Blackbird Main artifact Module descriptor
module tools.jackson.module.blackbird
{
    requires java.logging;

    requires tools.jackson.core;
    requires transitive tools.jackson.databind;

    exports tools.jackson.module.blackbird;
    // Internal supertypes of generated hidden classes; exported so codecs
    // defined in user-module contexts can resolve them. Not API.
    exports tools.jackson.module.blackbird.internal;

    provides tools.jackson.databind.JacksonModule with
        tools.jackson.module.blackbird.BlackbirdModule;
}
