// Manually composed on 14-Mar-2019 -- Moditect maven plugin failed to generate

module com.fasterxml.jackson.module.mrbean {
    requires transitive com.fasterxml.jackson.databind;

    provides com.fasterxml.jackson.databind.Module with
        com.fasterxml.jackson.module.mrbean.MrBeanModule;
}
