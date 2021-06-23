package com.fasterxml.jackson.module.noctordeser;

import com.fasterxml.jackson.core.Version;

/**
 * @since 2.13
 */
public class NoCtorModule extends com.fasterxml.jackson.databind.Module
{
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
        context.addDeserializationProblemHandler(new MissingInstantiatorHandler());
    }
}
