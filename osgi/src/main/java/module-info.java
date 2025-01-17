// OSGi module Main artifact Module descriptor
module tools.jackson.module.osgi
{
    requires tools.jackson.core;
    requires transitive tools.jackson.databind;
    requires org.osgi.core;

    exports tools.jackson.module.osgi;

    // NOTE! Does NOT expose Module via SPI as it can not provide 0-args constructor
}
