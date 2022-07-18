module tools.jackson.module.mrbean {
    requires transitive tools.jackson.databind;

    exports tools.jackson.module.mrbean;

    provides tools.jackson.databind.JacksonModule with
        tools.jackson.module.mrbean.MrBeanModule;
}
