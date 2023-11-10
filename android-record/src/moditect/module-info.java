module com.fasterxml.jackson.module.androidrecord {

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;

    exports com.fasterxml.jackson.module.androidrecord;

    provides com.fasterxml.jackson.databind.Module with
        com.fasterxml.jackson.module.androidrecord.AndroidRecordModule;
}
