module com.fasterxml.jackson.module.enhance.deser {

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    exports com.fasterxml.jackson.module.enhance.deser;

    provides com.fasterxml.jackson.databind.Module with
        com.fasterxml.jackson.module.enhance.deser.EnhanceDeserModule;
}
