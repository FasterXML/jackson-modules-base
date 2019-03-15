// Generated 14-Mar-2019 using Moditect maven plugin
module com.fasterxml.jackson.module.osgi {
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires org.osgi.core;

    exports com.fasterxml.jackson.module.osgi;

    // NOTE! Does NOT expose Module via SPI as it can not provide 0-args constructor
}
