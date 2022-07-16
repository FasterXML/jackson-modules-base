module tools.jackson.module.noctordeser {

    requires tools.jackson.core;
    requires tools.jackson.databind;

    exports tools.jackson.module.noctordeser;

    provides tools.jackson.databind.Module with
        tools.jackson.module.noctordeser.NoCtorDeserModule;
}
