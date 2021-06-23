module com.fasterxml.jackson.module.noctordeser {

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    exports com.fasterxml.jackson.module.noctordeser;

    provides com.fasterxml.jackson.databind.Module with
        com.fasterxml.jackson.module.noctordeser.NoCtorDeserModule;
}
