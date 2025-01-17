// Mr Bean Main artifact Module descriptor
module tools.jackson.module.mrbean
{
    requires com.fasterxml.jackson.annotation;
    requires tools.jackson.core;
    requires transitive tools.jackson.databind;

    requires static net.bytebuddy;

    exports tools.jackson.module.mrbean;

    provides tools.jackson.databind.JacksonModule with
        tools.jackson.module.mrbean.MrBeanModule;
}
