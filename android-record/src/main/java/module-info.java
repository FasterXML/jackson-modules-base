// Android-Record Main artifact Module descriptor
module tools.jackson.module.androidrecord {

    requires com.fasterxml.jackson.annotation;

    requires tools.jackson.core;
    requires tools.jackson.databind;

    exports tools.jackson.module.androidrecord;

    provides tools.jackson.databind.JacksonModule with
        tools.jackson.module.androidrecord.AndroidRecordModule;
}
