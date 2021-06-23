package com.fasterxml.jackson.module.enhance.deser;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.module.enhance.deser.handler.MissingInstantiatorHandler;

public class EnhanceDeserModule extends Module {

    @Override
    public String getModuleName() {
        return getClass().getSimpleName();
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addDeserializationProblemHandler(MissingInstantiatorHandler.getInstance());
    }
}
