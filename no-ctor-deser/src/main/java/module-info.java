// No-Constructor module Main artifact Module descriptor
module tools.jackson.module.noctordeser
{
    requires tools.jackson.core;
    requires transitive tools.jackson.databind;

    requires jdk.unsupported; 

    exports tools.jackson.module.noctordeser;

    provides tools.jackson.databind.JacksonModule with
        tools.jackson.module.noctordeser.NoCtorDeserModule;
}
