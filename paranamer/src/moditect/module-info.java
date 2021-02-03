// Manually composed on 14-Mar-2019 -- Moditect maven plugin failed to generate

module com.fasterxml.jackson.module.paranamer {
    requires com.fasterxml.jackson.databind;
    requires paranamer;

    provides com.fasterxml.jackson.databind.JacksonModule with
        com.fasterxml.jackson.module.paranamer.ParanamerModule;
}
